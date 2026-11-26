package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.auth.service.AccessScope;
import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.service.StockTransfer;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseRole;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.master.service.WarehouseRoles;
import com.bakery.api.production.dto.ProductionPoolDto.ConfirmLine;
import com.bakery.api.production.dto.ProductionPoolDto.ConfirmRequest;
import com.bakery.api.production.dto.ProductionPoolDto.Created;
import com.bakery.api.production.dto.ProductionPoolDto.CreatedPick;
import com.bakery.api.production.dto.ProductionPoolDto.Doc;
import com.bakery.api.production.dto.ProductionPoolDto.Held;
import com.bakery.api.production.dto.ProductionPoolDto.PickLineRequest;
import com.bakery.api.production.dto.ProductionPoolDto.PickRequest;
import com.bakery.api.production.dto.ProductionPoolDto.Pickable;
import com.bakery.api.production.dto.ProductionPoolDto.PickableItem;
import com.bakery.api.production.dto.ProductionPoolDto.Pool;
import com.bakery.api.production.dto.ProductionPoolDto.PoolItem;
import com.bakery.api.production.dto.ProductionPoolDto.Ref;
import com.bakery.api.production.dto.ProductionPoolDto.SessionInfo;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionSession;
import com.bakery.api.production.entity.SessionStatus;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.api.production.repository.ProductionSessionRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.ProductionType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 24/§5.1–§5.4: phiếu lấy NL vào bảng tạm: tự tạo theo kế hoạch / phiếu SX, bếp tự tạo, lấy thêm, xác nhận lấy, xóa;
 * và xem bảng tạm. Đóng phiên ở {@link ProductionSessionService}.
 */
@Service
@RequiredArgsConstructor
public class ProductionPickService {

    static final Set<String> MANUAL_REASONS = Set.of("MANUAL", "EXTRA_DAMAGED", "EXTRA_CUSTOMER", "EXTRA_OTHER");
    static final String REASON_PLAN = "PLAN";

    private final InventoryRequestRepository requestRepository;
    private final ProductionRequestRepository productionRequestRepository;
    private final ProductionSessionRepository sessionRepository;
    private final ProductionSessionService sessions;
    private final ProductionNeeds needs;
    private final WarehouseRoles roles;
    private final WarehouseRepository warehouseRepository;
    private final ItemLookupRepository itemRepository;
    private final StockTransfer stockTransfer;
    private final WipDocs docs;
    private final EntityManager em;
    private final AccessScope scope;
    private final BakeryActorResolver actorResolver;

    // ── Quy tắc thuần ───────────────────────────────────────────────────────

    /** Lý do không lấy thêm được cho phiếu SX {@code pr}, null nếu được (§5.4). */
    static String extraTargetBlocker(ProductionRequest pr) {
        if (pr == null) return "Lấy thêm NL phải gắn một phiếu SX";
        if (!ProductionSessionService.isHoldable(pr)) return "Phiếu " + pr.getCode() + " chưa duyệt hoặc đã hoàn thành, không lấy thêm được";
        return null;
    }

    /** Kiểm lý do + ghi chú + số bị hư của yêu cầu tạo phiếu. Trả lý do (400) hoặc null. */
    static String requestBlocker(String reason, String note, List<PickLineRequest> lines) {
        if (reason == null || !MANUAL_REASONS.contains(reason)) return "Lý do lấy không hợp lệ (" + String.join(", ", new java.util.TreeSet<>(MANUAL_REASONS)) + ")";
        if ("EXTRA_OTHER".equals(reason) && (note == null || note.isBlank())) return "Lấy thêm lý do Khác phải ghi chú";
        if (lines == null || lines.isEmpty()) return "Chưa chọn mặt hàng nào";
        for (PickLineRequest l : lines) {
            if (l.warehouseId() == null || l.itemId() == null) return "Dòng thiếu kho hoặc mặt hàng";
            if (l.qty() == null || l.qty().signum() <= 0) return "Số lượng lấy phải lớn hơn 0";
            if (l.damagedQty() != null && l.damagedQty().signum() < 0) return "Số bị hư không được âm";
            if (l.damagedQty() != null && l.damagedQty().signum() > 0 && !"EXTRA_DAMAGED".equals(reason)) return "Số bị hư chỉ dùng với lý do NL hư hỏng";
        }
        return null;
    }

    // ── Tạo tự động (§5.1) ──────────────────────────────────────────────────

    /**
     * Phiếu lấy theo kế hoạch ngày {@code date} cho mọi phiếu DAILY còn dòng chưa xong. Chỉ tạo phần còn thiếu so với các
     * phiếu lấy DAILY đã có của ngày đó (kể cả chưa xác nhận) → gọi lại không tạo trùng.
     */
    @Transactional
    public Created generate(LocalDate date) {
        List<ProductionRequest> prs = productionRequestRepository.findByProductionTypeAndProductionDate(ProductionType.DAILY, date).stream()
                .filter(p -> p.getStatus() == EntityStatus.ACTIVE && p.getApprovalStatus() != ApprovalStatus.REJECTED).toList();
        Map<UUID, ProductionNeeds.Need> total = new LinkedHashMap<>();
        for (ProductionRequest p : prs) needs.forRequest(p, true).forEach((id, n) -> total.merge(id, n, (a, b) -> new ProductionNeeds.Need(a.item(), a.qty().add(b.qty()))));
        List<InventoryRequest> existing = requestRepository.findByRequestTypeAndRequestDateAndPickReasonAndProductionRequestIsNull(
                InventoryRequestType.PICK, date, REASON_PLAN).stream()
                .filter(r -> r.getStatus() == EntityStatus.ACTIVE && r.getApprovalStatus() != ApprovalStatus.REJECTED).toList();
        return createPlanPicks(total, existing, date, null);
    }

    /** Phiếu lấy gắn phiếu ORDER / SEMI vừa duyệt; lô lấy về được giữ cho phiếu. Idempotent. */
    @Transactional
    public Created generateForRequest(ProductionRequest pr) {
        if (pr.getProductionType() == ProductionType.DAILY) return new Created(List.of());
        List<InventoryRequest> existing = requestRepository.findByProductionRequestIdAndRequestType(pr.getId(), InventoryRequestType.PICK).stream()
                .filter(r -> r.getStatus() == EntityStatus.ACTIVE && r.getApprovalStatus() != ApprovalStatus.REJECTED).toList();
        return createPlanPicks(needs.forRequest(pr, true), existing, pr.getProductionDate(), pr);
    }

    private Created createPlanPicks(Map<UUID, ProductionNeeds.Need> total, List<InventoryRequest> existing, LocalDate date, ProductionRequest pr) {
        Map<UUID, BigDecimal> have = new LinkedHashMap<>();
        for (InventoryRequest r : existing) {
            for (InventoryRequestLine l : r.getLines()) {
                BigDecimal q = l.getReceivedQty() != null ? l.getReceivedQty() : l.getQuantity();
                have.merge(l.getItem().getId(), q, BigDecimal::add);
            }
        }
        List<PickPlanner.Want> wants = new ArrayList<>();
        for (var n : total.values()) {
            BigDecimal left = n.qty().subtract(have.getOrDefault(n.item().getId(), BigDecimal.ZERO));
            if (left.signum() > 0) {
                Warehouse home = n.item().getDefaultWarehouse();
                wants.add(new PickPlanner.Want(n.item().getId(), left, home != null ? home.getId() : null));
            }
        }
        if (wants.isEmpty()) return new Created(List.of());
        List<Warehouse> sources = roles.withRole(WarehouseRole.PRODUCTION_SOURCE);
        Map<String, BigDecimal> stock = stockOf(sources.stream().map(Warehouse::getId).toList(),
                wants.stream().map(PickPlanner.Want::itemId).toList());
        List<PickPlanner.Alloc> allocs = PickPlanner.allocate(wants, sources.stream().map(Warehouse::getId).toList(),
                (w, i) -> stock.getOrDefault(w + "|" + i, BigDecimal.ZERO));
        return save(allocs, date, REASON_PLAN, null, pr);
    }

    /** Một phiếu PICK mỗi kho nguồn. */
    private Created save(List<PickPlanner.Alloc> allocs, LocalDate date, String reason, String note, ProductionRequest pr) {
        Warehouse wip = roles.workInProgress();
        Map<UUID, List<PickPlanner.Alloc>> byWh = allocs.stream().collect(Collectors.groupingBy(PickPlanner.Alloc::warehouseId, LinkedHashMap::new, Collectors.toList()));
        List<CreatedPick> out = new ArrayList<>();
        for (var e : byWh.entrySet()) {
            Warehouse src = warehouseRepository.findById(e.getKey()).orElseThrow(() -> new ResourceNotFoundException("Warehouse", e.getKey()));
            InventoryRequest doc = requestRepository.save(docs.newDoc(InventoryRequestType.PICK, date, src, wip, pr, reason, note, null));
            for (PickPlanner.Alloc a : e.getValue()) {
                Item item = itemRepository.findById(a.itemId()).orElseThrow(() -> new ResourceNotFoundException("Item", a.itemId()));
                docs.addLine(doc, item, a.qty()).setStockShort(a.stockShort());
            }
            requestRepository.save(doc);
            out.add(new CreatedPick(doc.getId(), doc.getCode(), Ref.of(src)));
        }
        return new Created(out);
    }

    // ── Bếp tự tạo / lấy thêm (§5.2, §5.4) ──────────────────────────────────

    @Transactional
    public Created create(PickRequest req) {
        String reason = req.reason();
        String blocked = requestBlocker(reason, req.note(), req.lines());
        if (blocked != null) throw new IllegalArgumentException(blocked);
        boolean extra = reason.startsWith("EXTRA_");
        if (extra) scope.require("PROD_REQUESTS", "EXTRA_MATERIAL");
        ProductionRequest pr = req.productionRequestId() == null ? null
                : productionRequestRepository.findById(req.productionRequestId())
                        .orElseThrow(() -> new ResourceNotFoundException("ProductionRequest", req.productionRequestId()));
        if (extra || pr != null) {
            String m = extraTargetBlocker(pr);
            if (m != null) throw new IllegalStateException(m);
        }
        Warehouse wip = roles.workInProgress();
        List<Warehouse> sources = roles.withRole(WarehouseRole.PRODUCTION_SOURCE);
        Set<UUID> sourceIds = sources.stream().map(Warehouse::getId).collect(Collectors.toSet());

        // Kiểm hết dòng trước khi ghi
        Map<UUID, Item> items = new LinkedHashMap<>();
        for (PickLineRequest l : req.lines()) {
            if (!sourceIds.contains(l.warehouseId())) throw new IllegalStateException("Chỉ lấy được ở kho Sản xuất lấy NL");
            Item item = itemRepository.findById(l.itemId()).orElseThrow(() -> new ResourceNotFoundException("Item", l.itemId()));
            requireMaterial(item);
            items.put(item.getId(), item);
        }
        Map<String, BigDecimal> stock = stockOf(new ArrayList<>(sourceIds), new ArrayList<>(items.keySet()));
        List<PickPlanner.Alloc> allocs = new ArrayList<>();
        for (PickLineRequest l : req.lines()) {
            BigDecimal have = stock.getOrDefault(l.warehouseId() + "|" + l.itemId(), BigDecimal.ZERO);
            allocs.add(new PickPlanner.Alloc(l.warehouseId(), l.itemId(), l.qty(), l.qty().compareTo(have) > 0));
        }

        ProductionSession open = sessions.currentOpen(wip.getStore());
        LocalDate date = open != null ? open.getSessionDate() : WipDocs.today();

        // Lấy thêm vì hư: ghi hao hụt ngay phần bị hư ở bảng tạm, gắn phiếu SX (trừ lô giữ cho phiếu trước)
        InventoryRequest writeOff = null;
        for (PickLineRequest l : req.lines()) {
            if (l.damagedQty() == null || l.damagedQty().signum() <= 0) continue;
            if (writeOff == null) {
                writeOff = requestRepository.save(docs.newDoc(InventoryRequestType.WRITE_OFF, date, wip, null, pr, null, req.note(),
                        open != null ? open.getId() : null));
            }
            docs.writeOffLine(writeOff, wip, items.get(l.itemId()), l.damagedQty(), pr.getId(), false, null, "DAMAGED", req.note());
        }
        if (writeOff != null) requestRepository.save(writeOff);
        return save(allocs, date, reason, req.note(), pr);
    }

    // ── Xác nhận lấy (§5.3) ─────────────────────────────────────────────────

    @Transactional
    public Doc confirm(UUID id, ConfirmRequest req) {
        InventoryRequest e = loadPick(id);
        if (e.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) throw new IllegalStateException("Phiếu đã xác nhận hoặc đã từ chối");
        Warehouse wip = roles.workInProgress();
        ProductionSession session = sessions.openFor(wip.getStore(), e.getRequestDate());

        Map<UUID, BigDecimal> byLine = new LinkedHashMap<>();
        List<ConfirmLine> extras = new ArrayList<>();
        for (ConfirmLine cl : req == null || req.lines() == null ? List.<ConfirmLine>of() : req.lines()) {
            if (cl.qty() == null || cl.qty().signum() < 0) throw new IllegalArgumentException("Số thực lấy không được âm");
            if (cl.lineId() != null) byLine.merge(cl.lineId(), cl.qty(), BigDecimal::add);
            else if (cl.itemId() != null) extras.add(cl);
            else throw new IllegalArgumentException("Dòng thiếu mặt hàng");
        }
        Set<UUID> known = e.getLines().stream().map(InventoryRequestLine::getId).collect(Collectors.toSet());
        for (UUID lid : byLine.keySet()) if (!known.contains(lid)) throw new IllegalArgumentException("Dòng " + lid + " không thuộc phiếu này");

        // Số thực lấy từng dòng có sẵn (không gửi = 0 = bỏ dòng)
        for (InventoryRequestLine l : new ArrayList<>(e.getLines())) {
            BigDecimal q = byLine.getOrDefault(l.getId(), BigDecimal.ZERO);
            if (q.signum() == 0) e.getLines().remove(l);
            else l.setReceivedQty(q);
        }
        for (ConfirmLine x : extras) {   // dòng thêm: chỉ NL/BTP
            if (x.qty().signum() == 0) continue;
            Item item = itemRepository.findById(x.itemId()).orElseThrow(() -> new ResourceNotFoundException("Item", x.itemId()));
            requireMaterial(item);
            InventoryRequestLine l = docs.addLine(e, item, BigDecimal.ZERO);
            l.setReceivedQty(x.qty());
        }
        if (e.getLines().isEmpty()) throw new IllegalStateException("Chưa nhập số thực lấy cho dòng nào");

        UUID holdFor = e.getProductionRequest() != null && ProductionSessionService.isHoldable(e.getProductionRequest())
                ? e.getProductionRequest().getId() : null;
        for (InventoryRequestLine l : e.getLines()) {
            BigDecimal left = stockTransfer.move(l.getItem(), l.getReceivedQty(), e.getSourceWarehouse(), wip, null, holdFor,
                    e.getRequestDate(), WipDocs.REF_TYPE, e.getId(),
                    "Lấy NL vào bảng tạm, phiếu " + e.getCode(), "Nhận NL vào bảng tạm, phiếu " + e.getCode());
            if (left.signum() > 0) {
                throw new IllegalStateException(l.getItem().getName() + ": kho " + e.getSourceWarehouse().getName()
                        + " chỉ còn " + WipDocs.plain(l.getReceivedQty().subtract(left)));
            }
        }
        e.setApprovalStatus(ApprovalStatus.APPROVED);
        e.setApprovedAt(java.time.Instant.now());
        e.setApprovedBy(actorResolver.currentUserId());
        e.setReceivedBy(actorResolver.currentUsername());
        e.setReceivedDate(WipDocs.today());
        e.setSessionId(session.getId());
        return Doc.from(requestRepository.save(e), scope.canSeeCost());
    }

    @Transactional
    public void delete(UUID id) {
        InventoryRequest e = loadPick(id);
        if (e.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) throw new IllegalStateException("Phiếu đã xác nhận, không xóa được");
        requestRepository.delete(e);
    }

    /** Xóa phiếu PICK chưa xác nhận gắn phiếu SX (hủy / từ chối / xóa phiếu SX, §5.7). */
    @Transactional
    public int deletePendingFor(UUID productionRequestId) {
        List<InventoryRequest> pending = requestRepository.findByProductionRequestIdAndRequestType(productionRequestId, InventoryRequestType.PICK)
                .stream().filter(r -> r.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL).toList();
        requestRepository.deleteAll(pending);
        return pending.size();
    }

    @Transactional(readOnly = true)
    public List<Doc> list(LocalDate from, LocalDate to) {
        LocalDate t = to != null ? to : WipDocs.today();
        LocalDate f = from != null ? from : t.minusDays(7);
        boolean seeCost = scope.canSeeCost();
        return requestRepository.findByRequestTypeInAndRequestDateBetweenOrderByRequestDateDescCreatedAtDesc(
                List.of(InventoryRequestType.PICK, InventoryRequestType.RETURN, InventoryRequestType.WRITE_OFF), f, t)
                .stream().map(r -> Doc.from(r, seeCost)).toList();
    }

    // ── Xem bảng tạm (§8) ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Pool pool() {
        Warehouse wip = roles.workInProgress();
        ProductionSession open = sessions.currentOpen(wip.getStore());
        SessionInfo cur = SessionInfo.from(open);
        SessionInfo stale = open != null && open.getSessionDate().isBefore(WipDocs.today()) ? cur : null;

        Map<UUID, BigDecimal> shared = new LinkedHashMap<>();
        Map<UUID, List<Held>> held = new LinkedHashMap<>();
        Map<UUID, String> prCodes = new LinkedHashMap<>();
        List<Object[]> stockRows = em.createNativeQuery(
                "SELECT item_id, reserved_for_request_id, SUM(qty_remaining) FROM stock_lot WHERE warehouse_id = :w AND qty_remaining > 0 GROUP BY item_id, reserved_for_request_id")
                .setParameter("w", wip.getId()).getResultList();
        for (Object[] r : stockRows) {
            UUID item = (UUID) r[0];
            UUID pr = (UUID) r[1];
            BigDecimal q = (BigDecimal) r[2];
            if (pr == null) shared.merge(item, q, BigDecimal::add);
            else {
                String code = prCodes.computeIfAbsent(pr, k -> productionRequestRepository.findById(k).map(ProductionRequest::getCode).orElse(null));
                held.computeIfAbsent(item, k -> new ArrayList<>()).add(new Held(pr, code, q));
            }
        }
        Map<UUID, BigDecimal> picked = new LinkedHashMap<>();
        Map<UUID, BigDecimal> written = new LinkedHashMap<>();
        Map<UUID, BigDecimal> consumed = new LinkedHashMap<>();
        if (open != null) {
            sumByItem(picked, "SELECT rl.item_id, SUM(rl.received_qty) FROM inventory_request_line rl JOIN inventory_request r ON r.id = rl.inventory_request_id "
                    + "WHERE r.session_id = :s AND r.request_type = 'PICK' GROUP BY rl.item_id", "s", open.getId());
            sumByItem(written, "SELECT rl.item_id, SUM(rl.quantity) FROM inventory_request_line rl JOIN inventory_request r ON r.id = rl.inventory_request_id "
                    + "WHERE r.session_id = :s AND r.request_type = 'WRITE_OFF' GROUP BY rl.item_id", "s", open.getId());
            List<Object[]> rows = em.createNativeQuery(
                    "SELECT l.item_id, SUM(m.qty) FROM stock_movement m JOIN stock_lot l ON l.id = m.lot_id "
                    + "WHERE l.warehouse_id = :w AND m.movement_type = 'OUT' AND m.ref_type = 'PRODUCTION_REQUEST' AND m.created_at >= :t GROUP BY l.item_id")
                    .setParameter("w", wip.getId()).setParameter("t", open.getOpenedAt()).getResultList();
            for (Object[] r : rows) consumed.put((UUID) r[0], (BigDecimal) r[1]);
        }
        Set<UUID> ids = new java.util.LinkedHashSet<>(shared.keySet());
        ids.addAll(held.keySet());
        ids.addAll(picked.keySet());
        List<PoolItem> items = new ArrayList<>();
        for (UUID id : ids) {
            Item it = itemRepository.findById(id).orElse(null);
            if (it == null) continue;
            Warehouse home = it.getDefaultWarehouse();
            items.add(new PoolItem(id, it.getName(), it.getUnit(), home == null ? null : new Ref(home.getId(), home.getCode(), home.getName()),
                    picked.getOrDefault(id, BigDecimal.ZERO), consumed.getOrDefault(id, BigDecimal.ZERO),
                    written.getOrDefault(id, BigDecimal.ZERO), shared.getOrDefault(id, BigDecimal.ZERO),
                    held.getOrDefault(id, List.of())));
        }
        items.sort(java.util.Comparator.comparing(PoolItem::name, java.text.Collator.getInstance(java.util.Locale.forLanguageTag("vi"))));
        return new Pool(Ref.of(wip), cur, stale, items);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Pickable> pickable() {
        List<Warehouse> sources = roles.withRole(WarehouseRole.PRODUCTION_SOURCE);
        if (sources.isEmpty()) return List.of();
        List<Object[]> rows = em.createNativeQuery(
                "SELECT l.warehouse_id, i.id, i.name, i.item_type, i.unit, SUM(l.qty_remaining) FROM stock_lot l JOIN item i ON i.id = l.item_id "
                + "WHERE l.warehouse_id IN (:ws) AND l.qty_remaining > 0 AND i.status = 'ACTIVE' AND i.item_type IN ('INGREDIENT', 'SEMI_PRODUCT') "
                + "GROUP BY l.warehouse_id, i.id, i.name, i.item_type, i.unit ORDER BY i.name")
                .setParameter("ws", sources.stream().map(Warehouse::getId).toList()).getResultList();
        Map<UUID, List<PickableItem>> byWh = new LinkedHashMap<>();
        for (Object[] r : rows) {
            byWh.computeIfAbsent((UUID) r[0], k -> new ArrayList<>())
                    .add(new PickableItem((UUID) r[1], (String) r[2], (String) r[3], (String) r[4], (BigDecimal) r[5]));
        }
        return sources.stream().map(w -> new Pickable(Ref.of(w), byWh.getOrDefault(w.getId(), List.of()))).toList();
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    private InventoryRequest loadPick(UUID id) {
        InventoryRequest e = requestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("InventoryRequest", id));
        if (e.getRequestType() != InventoryRequestType.PICK) throw new IllegalStateException("Không phải phiếu lấy NL");
        return e;
    }

    /** Dòng thêm / dòng lấy thêm chỉ là NL hoặc BTP. */
    private static void requireMaterial(Item item) {
        Object u = Hibernate.unproxy(item);
        if (!(u instanceof Ingredient) && !(u instanceof SemiProduct)) {
            throw new IllegalStateException(item.getName() + " không phải nguyên liệu hoặc hàng gia công");
        }
    }

    /** Tồn theo "kho|mặt hàng". */
    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> stockOf(List<UUID> warehouseIds, List<UUID> itemIds) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (warehouseIds.isEmpty() || itemIds.isEmpty()) return out;
        List<Object[]> rows = em.createNativeQuery(
                "SELECT warehouse_id, item_id, SUM(qty_remaining) FROM stock_lot WHERE warehouse_id IN (:ws) AND item_id IN (:is) AND qty_remaining > 0 GROUP BY warehouse_id, item_id")
                .setParameter("ws", warehouseIds).setParameter("is", itemIds).getResultList();
        for (Object[] r : rows) out.put(r[0] + "|" + r[1], ((BigDecimal) r[2]).setScale(4, RoundingMode.HALF_UP));
        return out;
    }

    @SuppressWarnings("unchecked")
    private void sumByItem(Map<UUID, BigDecimal> into, String sql, String param, Object value) {
        List<Object[]> rows = em.createNativeQuery(sql).setParameter(param, value).getResultList();
        for (Object[] r : rows) into.put((UUID) r[0], r[1] == null ? BigDecimal.ZERO : (BigDecimal) r[1]);
    }
}
