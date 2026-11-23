package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.CodeValue;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.CodeValueRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.dto.ProductionMaterialDto;
import com.bakery.api.production.dto.ProductionMaterialDto.Line;
import com.bakery.api.production.entity.ProductionMaterial;
import com.bakery.api.production.entity.ProductionMaterialWriteoff;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.production.repository.ProductionMaterialRepository;
import com.bakery.api.production.repository.ProductionMaterialWriteoffRepository;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.entity.ProductionLineStatus;
import com.bakery.framework.entity.ProductionType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 20: lấy NL cho phiếu SX bán thành phẩm (SEMI) qua bảng tạm "Đang sản xuất".
 *
 * <p>NL rời kho là trừ tồn ngay (movement OUT) và vào {@link ProductionMaterial} gắn với phiếu. Hoàn thành dòng
 * trừ bảng tạm theo công thức × số thực làm (không tạo movement). Phần dư phải trả lại đúng lô hoặc ghi hao hụt
 * trước khi bếp trưởng đóng phiếu. Lỗi nhập liệu → 400 ({@link IllegalArgumentException}); sai trạng thái/thiếu → 422
 * ({@link IllegalStateException}).
 */
@Service
@RequiredArgsConstructor
public class ProductionMaterialService {

    static final String REF_TYPE = "PRODUCTION_MATERIAL";
    static final String REASON_GROUP = "ADJUSTMENT_REASON";   // 08/D4
    static final String REASON_OTHER = "OTHER";
    /** Kho được lấy NL: Kho Tổng + khu sản xuất (20/BE-1). */
    private static final Set<String> PICK_WAREHOUSES = Set.of("MAIN", "KITCHEN", "COLD");
    private static final List<String> STOCK_ORDER = List.of("KITCHEN", "COLD", "MAIN");

    private final ProductionRequestRepository requestRepository;
    private final ProductionMaterialRepository materialRepository;
    private final ProductionMaterialWriteoffRepository writeoffRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final CodeValueRepository codeValueRepository;
    private final ProductionIngredientService ingredientService;
    private final BakeryActorResolver actorResolver;

    // ── Đọc ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProductionMaterialDto.Materials materials(UUID requestId) {
        return build(semiRequest(requestId));
    }

    /** materialStatus của phiếu: null nếu không phải SEMI. */
    @Transactional(readOnly = true)
    public String statusOf(ProductionRequest pr) {
        if (pr.getProductionType() != ProductionType.SEMI) return null;
        if (pr.getClosedAt() != null) return "CLOSED";
        Object[] s = materialRepository.summary(pr.getId()).get(0);
        boolean any = ((Number) s[0]).longValue() > 0;
        BigDecimal remaining = (BigDecimal) s[1];
        boolean allDone = !pr.getLines().isEmpty() && pr.getLines().stream()
                .allMatch(l -> l.getLineStatus() == ProductionLineStatus.COMPLETED);
        if (!allDone) return any ? "IN_PROGRESS" : "NONE";
        return remaining.signum() > 0 ? "TO_SETTLE" : "READY_TO_CLOSE";
    }

    // ── Lấy NL (BE-1) ────────────────────────────────────────────

    @Transactional
    public ProductionMaterialDto.Materials pick(UUID requestId, ProductionMaterialDto.PickRequest req) {
        ProductionRequest pr = semiRequest(requestId);
        if (pr.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Phiếu SX chưa được duyệt, chưa lấy nguyên liệu được.");
        }
        requireOpen(pr);
        if (req == null || req.warehouseId() == null) throw new IllegalArgumentException("Thiếu kho lấy nguyên liệu.");
        Warehouse wh = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.warehouseId()));
        if (!PICK_WAREHOUSES.contains(wh.getCode())) {
            throw new IllegalArgumentException("Chỉ lấy nguyên liệu ở Kho Tổng, Kho Bếp hoặc Phòng Lạnh.");
        }
        requireLines(req.lines());

        Map<UUID, ProductionIngredientService.Need> needs = needs(pr);
        // Kiểm hết trước khi động đến tồn: lỗi nhập liệu → 400, cả request một transaction
        for (Line l : req.lines()) {
            positive(l);
            if (!needs.containsKey(l.itemId())) {
                throw new IllegalArgumentException("Nguyên liệu " + l.itemId() + " không có trong nhu cầu của phiếu "
                        + pr.getCode());
            }
        }
        Instant now = Instant.now();
        String who = actorResolver.currentUsername();
        for (Line l : req.lines()) {
            Item item = needs.get(l.itemId()).item();
            BigDecimal want = scale(l.qty());
            List<StockLot> lots = stockLotRepository
                    .findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            item.getId(), wh.getId(), BigDecimal.ZERO);
            BigDecimal avail = lots.stream().map(StockLot::getQtyRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (avail.compareTo(want) < 0) {
                throw new IllegalStateException(item.getName() + ": kho " + wh.getName() + " chỉ còn " + plain(avail));
            }
            BigDecimal left = want;
            for (StockLot lot : lots) {
                if (left.signum() <= 0) break;
                BigDecimal take = left.min(lot.getQtyRemaining());
                lot.setQtyRemaining(lot.getQtyRemaining().subtract(take));
                stockLotRepository.save(lot);

                StockMovement mv = new StockMovement();
                mv.setLot(lot);
                mv.setMovementType(MovementType.OUT);
                mv.setQty(take);
                mv.setRefId(pr.getId());
                mv.setRefType(REF_TYPE);
                mv.setNote("Lấy NL cho phiếu " + pr.getCode());
                stockMovementRepository.save(mv);

                ProductionMaterial m = new ProductionMaterial();
                m.setProductionRequestId(pr.getId());
                m.setItem(item);
                m.setSourceLot(lot);
                m.setSourceWarehouse(wh);
                m.setUnitCost(lot.getUnitCost());
                m.setQtyPicked(take);
                m.setPickedBy(who);
                m.setPickedAt(now);
                m.setCreatedBy(who);
                m.setUpdatedBy(who);
                materialRepository.save(m);
                left = left.subtract(take);
            }
        }
        return build(pr);
    }

    // ── Hoàn thành dòng trừ bảng tạm (BE-2) ──────────────────────

    /**
     * Trừ bảng tạm theo công thức × {@code qtyProduced} (FIFO theo lúc lấy), tăng qty_consumed, KHÔNG tạo movement.
     * Thiếu → 422 và không trừ gì.
     */
    @Transactional
    public void consume(ProductionRequest pr, ProductionRequestLine line, BigDecimal qtyProduced) {
        Map<UUID, ProductionIngredientService.Need> needs = ingredientService.rawNeeds(line, qtyProduced);
        if (needs.isEmpty()) return;
        List<ProductionMaterial> rows = materialRepository.findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(pr.getId());
        // Kiểm đủ TRƯỚC khi trừ
        for (var n : needs.values()) {
            BigDecimal need = scale(n.qty());
            BigDecimal avail = rows.stream().filter(r -> r.getItem().getId().equals(n.item().getId()))
                    .map(ProductionMaterial::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (avail.compareTo(need) < 0) {
                throw new IllegalStateException("Chưa lấy đủ nguyên liệu: " + n.item().getName() + " thiếu "
                        + plain(need.subtract(avail)) + " " + (n.item().getUnit() == null ? "" : n.item().getUnit())
                        + ". Bấm Lấy thêm.");
            }
        }
        String who = actorResolver.currentUsername();
        for (var n : needs.values()) {
            BigDecimal left = scale(n.qty());
            for (ProductionMaterial r : rows) {
                if (left.signum() <= 0) break;
                if (!r.getItem().getId().equals(n.item().getId()) || r.remaining().signum() <= 0) continue;
                BigDecimal take = left.min(r.remaining());
                r.setQtyConsumed(r.getQtyConsumed().add(take));
                r.setUpdatedBy(who);
                materialRepository.save(r);
                left = left.subtract(take);
            }
        }
    }

    // ── Đối soát (BE-3) ──────────────────────────────────────────

    /** Trả NL dư về đúng lô nguồn, lô lấy sau cùng trước (LIFO). */
    @Transactional
    public ProductionMaterialDto.Materials giveBack(UUID requestId, ProductionMaterialDto.ReturnRequest req) {
        ProductionRequest pr = semiRequest(requestId);
        requireOpen(pr);
        List<Line> lines = req == null ? null : req.lines();
        requireLines(lines);
        List<ProductionMaterial> rows = materialRepository.findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(pr.getId());
        checkRemaining(rows, lines);
        String who = actorResolver.currentUsername();
        for (Line l : lines) {
            BigDecimal left = scale(l.qty());
            for (ProductionMaterial r : reversed(rows)) {
                if (left.signum() <= 0) break;
                if (!r.getItem().getId().equals(l.itemId()) || r.remaining().signum() <= 0) continue;
                BigDecimal take = left.min(r.remaining());
                StockLot lot = r.getSourceLot();
                lot.setQtyRemaining(lot.getQtyRemaining().add(take));
                stockLotRepository.save(lot);

                StockMovement mv = new StockMovement();
                mv.setLot(lot);
                mv.setMovementType(MovementType.IN);
                mv.setQty(take);
                mv.setRefId(pr.getId());
                mv.setRefType(REF_TYPE);
                mv.setNote("Trả NL dư phiếu " + pr.getCode());
                stockMovementRepository.save(mv);

                r.setQtyReturned(r.getQtyReturned().add(take));
                r.setUpdatedBy(who);
                materialRepository.save(r);
                left = left.subtract(take);
            }
        }
        return build(pr);
    }

    /** Ghi hao hụt NL dư: có lý do (ADJUSTMENT_REASON), không tạo movement (NL đã rời kho lúc lấy). */
    @Transactional
    public ProductionMaterialDto.Materials writeOff(UUID requestId, ProductionMaterialDto.WriteOffRequest req) {
        ProductionRequest pr = semiRequest(requestId);
        requireOpen(pr);
        List<ProductionMaterialDto.WriteOffLine> lines = req == null ? null : req.lines();
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Thiếu danh sách nguyên liệu.");
        Set<String> reasons = codeValueRepository.findByGroupKeyAndStatusOrderBySortOrderAsc(REASON_GROUP, EntityStatus.ACTIVE)
                .stream().map(CodeValue::getCode).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<ProductionMaterial> rows = materialRepository.findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(pr.getId());
        List<Line> plain = new ArrayList<>();
        for (var l : lines) {
            Line pl = new Line(l.itemId(), l.qty());
            positive(pl);
            plain.add(pl);
            String reason = l.reasonCode() == null ? null : l.reasonCode().trim();
            String name = nameOf(rows, l.itemId());
            if (reason == null || reason.isEmpty() || !reasons.contains(reason)) {
                throw new IllegalArgumentException(name + ": lý do không hợp lệ (" + String.join(", ", reasons) + ")");
            }
            if (REASON_OTHER.equals(reason) && (l.note() == null || l.note().isBlank())) {
                throw new IllegalArgumentException(name + ": lý do \"Khác\" bắt buộc ghi chú");
            }
        }
        checkRemaining(rows, plain);
        String who = actorResolver.currentUsername();
        for (var l : lines) {
            BigDecimal left = scale(l.qty());
            for (ProductionMaterial r : rows) {
                if (left.signum() <= 0) break;
                if (!r.getItem().getId().equals(l.itemId()) || r.remaining().signum() <= 0) continue;
                BigDecimal take = left.min(r.remaining());
                ProductionMaterialWriteoff w = new ProductionMaterialWriteoff();
                w.setProductionMaterialId(r.getId());
                w.setQty(take);
                w.setReasonCode(l.reasonCode().trim());
                w.setNote(l.note());
                w.setAmount(r.getUnitCost() == null ? null : take.multiply(r.getUnitCost()).setScale(2, RoundingMode.HALF_UP));
                w.setCreatedBy(who);
                writeoffRepository.save(w);

                r.setQtyWrittenOff(r.getQtyWrittenOff().add(take));
                r.setUpdatedBy(who);
                materialRepository.save(r);
                left = left.subtract(take);
            }
        }
        return build(pr);
    }

    /** Bếp trưởng đóng phiếu: mọi dòng đã hoàn thành và bảng tạm về 0. Không đạt → 422 nêu lý do. */
    @Transactional
    public void close(ProductionRequest pr) {
        if (pr.getProductionType() != ProductionType.SEMI) {
            throw new IllegalStateException("Chỉ phiếu SX bán thành phẩm (SEMI) mới có bước đóng phiếu.");
        }
        if (pr.getClosedAt() != null) throw new IllegalStateException("Phiếu " + pr.getCode() + " đã đóng.");
        if (pr.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Phiếu SX chưa được duyệt.");
        }
        List<String> problems = new ArrayList<>();
        long open = pr.getLines().stream().filter(l -> l.getLineStatus() != ProductionLineStatus.COMPLETED).count();
        if (open > 0) problems.add(open + " dòng chưa hoàn thành");
        Map<UUID, BigDecimal> left = new LinkedHashMap<>();
        Map<UUID, Item> items = new LinkedHashMap<>();
        for (ProductionMaterial r : materialRepository.findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(pr.getId())) {
            if (r.remaining().signum() > 0) {
                left.merge(r.getItem().getId(), r.remaining(), BigDecimal::add);
                items.put(r.getItem().getId(), r.getItem());
            }
        }
        left.forEach((id, q) -> problems.add("còn dư " + items.get(id).getName() + " " + plain(q) + " "
                + (items.get(id).getUnit() == null ? "" : items.get(id).getUnit())));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Chưa đóng được phiếu " + pr.getCode() + ": " + String.join("; ", problems)
                    + (left.isEmpty() ? "" : ". Cần trả lại hoặc ghi hao hụt."));
        }
        pr.setClosedAt(Instant.now());
        pr.setClosedBy(actorResolver.currentUsername());
        requestRepository.save(pr);
    }

    /** Hủy/từ chối phiếu: phải đối soát hết bảng tạm trước (20/D8). */
    @Transactional(readOnly = true)
    public void requireSettled(ProductionRequest pr) {
        if (pr.getProductionType() != ProductionType.SEMI || pr.getId() == null) return;
        BigDecimal remaining = (BigDecimal) materialRepository.summary(pr.getId()).get(0)[1];
        if (remaining.signum() > 0) {
            throw new IllegalStateException("Còn nguyên liệu đang sản xuất, cần trả lại hoặc ghi hao hụt trước");
        }
    }

    // ── nội bộ ───────────────────────────────────────────────────

    private ProductionRequest semiRequest(UUID id) {
        ProductionRequest pr = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionRequest", id));
        if (pr.getProductionType() != ProductionType.SEMI) {
            throw new IllegalStateException("Chỉ phiếu SX bán thành phẩm (SEMI) có danh sách lấy nguyên liệu. Phiếu "
                    + pr.getCode() + " là " + pr.getProductionType() + ".");
        }
        return pr;
    }

    private void requireOpen(ProductionRequest pr) {
        if (pr.getClosedAt() != null) throw new IllegalStateException("Phiếu " + pr.getCode() + " đã đóng.");
    }

    private static void requireLines(List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Thiếu danh sách nguyên liệu.");
    }

    private static void positive(Line l) {
        if (l == null || l.itemId() == null) throw new IllegalArgumentException("Mỗi dòng cần itemId.");
        if (l.qty() == null || l.qty().signum() <= 0) throw new IllegalArgumentException("Số lượng phải lớn hơn 0.");
    }

    /** Mỗi NL: tổng qty yêu cầu ≤ còn trong bảng tạm, kể cả khi một NL xuất hiện nhiều dòng. */
    private void checkRemaining(List<ProductionMaterial> rows, List<Line> lines) {
        Map<UUID, BigDecimal> asked = new LinkedHashMap<>();
        for (Line l : lines) {
            positive(l);
            asked.merge(l.itemId(), scale(l.qty()), BigDecimal::add);
        }
        asked.forEach((id, q) -> {
            BigDecimal rem = rows.stream().filter(r -> r.getItem().getId().equals(id))
                    .map(ProductionMaterial::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (q.compareTo(rem) > 0) {
                throw new IllegalArgumentException(nameOf(rows, id) + ": chỉ còn " + plain(rem)
                        + " trong bảng tạm, không xử lý được " + plain(q));
            }
        });
    }

    private static String nameOf(List<ProductionMaterial> rows, UUID itemId) {
        return rows.stream().filter(r -> r.getItem().getId().equals(itemId)).map(r -> r.getItem().getName())
                .findFirst().orElse(String.valueOf(itemId));
    }

    private static <T> List<T> reversed(List<T> l) {
        List<T> c = new ArrayList<>(l);
        Collections.reverse(c);
        return c;
    }

    private static BigDecimal scale(BigDecimal v) { return v.setScale(4, RoundingMode.HALF_UP); }

    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    /** Nhu cầu NL của cả phiếu = công thức × số kế hoạch từng dòng. */
    private Map<UUID, ProductionIngredientService.Need> needs(ProductionRequest pr) {
        Map<UUID, ProductionIngredientService.Need> out = new LinkedHashMap<>();
        for (ProductionRequestLine l : pr.getLines()) {
            if (l.getProduct() == null || l.getPlannedQty() == null) continue;
            ingredientService.rawNeeds(l, l.getPlannedQty()).forEach((id, n) ->
                    out.merge(id, new ProductionIngredientService.Need(n.item(), scale(n.qty())),
                            (a, b) -> new ProductionIngredientService.Need(a.item(), a.qty().add(b.qty()))));
        }
        return out;
    }

    private ProductionMaterialDto.Materials build(ProductionRequest pr) {
        Map<UUID, ProductionIngredientService.Need> needs = needs(pr);
        List<ProductionMaterial> rows = materialRepository.findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(pr.getId());
        Map<UUID, List<ProductionMaterial>> byItem = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getItem().getId(), LinkedHashMap::new, Collectors.toList()));
        // Item đã lấy nhưng nay không còn trong nhu cầu (sửa phiếu sau khi lấy) vẫn phải hiện để đối soát
        Map<UUID, Item> items = new LinkedHashMap<>();
        needs.forEach((id, n) -> items.put(id, n.item()));
        byItem.forEach((id, rs) -> items.putIfAbsent(id, rs.get(0).getItem()));

        Map<String, Warehouse> whs = new LinkedHashMap<>();
        for (String c : STOCK_ORDER) warehouseRepository.findByCode(c).ifPresent(w -> whs.put(c, w));

        List<ProductionMaterialDto.Row> out = new ArrayList<>();
        for (var e : items.entrySet()) {
            Item item = e.getValue();
            List<ProductionMaterial> rs = byItem.getOrDefault(e.getKey(), List.of());
            BigDecimal needed = needs.containsKey(e.getKey()) ? needs.get(e.getKey()).qty() : BigDecimal.ZERO.setScale(4);
            BigDecimal picked = sum(rs, ProductionMaterial::getQtyPicked);
            BigDecimal consumed = sum(rs, ProductionMaterial::getQtyConsumed);
            BigDecimal returned = sum(rs, ProductionMaterial::getQtyReturned);
            BigDecimal written = sum(rs, ProductionMaterial::getQtyWrittenOff);
            BigDecimal toPick = needed.subtract(picked).add(returned).add(written).max(BigDecimal.ZERO);

            List<ProductionMaterialDto.StockAt> stock = new ArrayList<>();
            for (Warehouse w : whs.values()) {
                BigDecimal q = stockLotRepository.findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                                item.getId(), w.getId(), BigDecimal.ZERO).stream()
                        .map(StockLot::getQtyRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (q.signum() > 0) stock.add(new ProductionMaterialDto.StockAt(w.getId(), ref(w), q));
            }
            // Một lượt lấy ở một kho = các dòng cùng (kho, người, lúc lấy)
            Map<String, ProductionMaterialDto.Pick> picks = new LinkedHashMap<>();
            for (ProductionMaterial r : rs) {
                String key = r.getSourceWarehouse().getId() + "|" + r.getPickedBy() + "|" + r.getPickedAt();
                picks.merge(key, new ProductionMaterialDto.Pick(r.getSourceWarehouse().getId(), ref(r.getSourceWarehouse()),
                                r.getQtyPicked(), r.getPickedBy(), r.getPickedAt()),
                        (a, b) -> new ProductionMaterialDto.Pick(a.warehouseId(), a.warehouse(),
                                a.qty().add(b.qty()), a.pickedBy(), a.pickedAt()));
            }
            out.add(new ProductionMaterialDto.Row(item.getId(), new ReferenceValue(item.getCode(), item.getName()),
                    item.getUnit(), needed, picked, consumed, returned, written,
                    picked.subtract(consumed).subtract(returned).subtract(written), toPick, stock,
                    new ArrayList<>(picks.values())));
        }
        return new ProductionMaterialDto.Materials(pr.getClosedAt() != null, pr.getClosedBy(), pr.getClosedAt(), out);
    }

    private static ReferenceValue ref(Warehouse w) { return new ReferenceValue(w.getCode(), w.getName()); }

    private static BigDecimal sum(List<ProductionMaterial> rs, java.util.function.Function<ProductionMaterial, BigDecimal> f) {
        return rs.stream().map(f).reduce(BigDecimal.ZERO.setScale(4), BigDecimal::add);
    }
}
