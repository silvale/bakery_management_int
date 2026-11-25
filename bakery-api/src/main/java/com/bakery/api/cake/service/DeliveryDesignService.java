package com.bakery.api.cake.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.cake.dto.DeliveryRecordAccessoryResponse;
import com.bakery.api.cake.dto.DeliveryRecordDesignResponse;
import com.bakery.api.cake.dto.DesignQtyRequest;
import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.CakeDesignAccessory;
import com.bakery.api.cake.entity.DeliveryRecordAccessory;
import com.bakery.api.cake.entity.DeliveryRecordDesign;
import com.bakery.api.cake.repository.DeliveryRecordAccessoryRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.cake.repository.CakeDesignRepository;
import com.bakery.api.cake.repository.DeliveryRecordDesignRepository;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.inventory.service.ProductionStock;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.CommandRequest;
import com.bakery.framework.entity.CommandStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.entity.ProductionType;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mẫu bánh trong luồng giao nhận: bếp chọn mẫu và phụ kiện thực dùng khi hoàn thành, phụ kiện trừ ngay lúc đó
 * (02/BE-2, BE-13); cửa hàng xác nhận mẫu thực nhận (02/BE-3) — không trừ phụ kiện nữa, trừ phiếu hoàn thành
 * trước BE-13 (chưa có cờ {@code accessories_deducted}) thì vẫn trừ theo mẫu cửa hàng nhận.
 */
@Service
@RequiredArgsConstructor
public class DeliveryDesignService {

    public static final String REF_TYPE = "CAKE_DESIGN";

    private final CakeDesignRepository designRepository;
    private final DeliveryRecordDesignRepository rowRepository;
    private final DeliveryRecordAccessoryRepository accessoryRepository;
    private final ItemLookupRepository itemRepository;
    private final ProductionStock productionStock;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    /** Kết quả xác nhận theo mẫu. */
    public record ConfirmResult(List<String> stockWarnings) {}

    // ── BE-2: bếp hoàn thành ─────────────────────────────────

    /**
     * Kiểm mẫu bếp chọn. Trả null nếu không áp dụng (phiếu SEMI, hoặc thành phẩm không có mẫu đang dùng);
     * ngược lại trả designId → qty (rỗng khi qtyProduced = 0). Phiếu DAILY và ORDER dùng mẫu như nhau (D15).
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> validateProduced(UUID productId, ProductionType type,
            BigDecimal qtyProduced, List<DesignQtyRequest> designs) {
        if (type == ProductionType.SEMI) return null;
        Set<UUID> active = designRepository.findByProductIdAndActiveTrue(productId).stream()
                .map(CakeDesign::getId).collect(Collectors.toSet());
        if (active.isEmpty()) return null;
        return DesignSplit.validate(designs, active, qtyProduced, "làm ra", this::codeOf);
    }

    /** Lưu số bánh bếp làm ra theo mẫu. Không động đến tồn phụ kiện (D6). */
    @Transactional
    public void saveProduced(DeliveryRecord dr, Map<UUID, BigDecimal> produced) {
        if (produced == null) return;
        for (Map.Entry<UUID, BigDecimal> en : produced.entrySet()) {
            DeliveryRecordDesign row = new DeliveryRecordDesign();
            row.setDeliveryRecord(dr);
            row.setCakeDesign(designRepository.getReferenceById(en.getKey()));
            row.setQtyProduced(en.getValue());
            rowRepository.save(row);
        }
    }

    // ── BE-13: phụ kiện bếp thực dùng, trừ ngay lúc hoàn thành ──

    /** Một dòng phụ kiện đã kiểm hợp lệ, chưa ghi/trừ. */
    public record AccessoryPlan(UUID designId, String designCode, Item item, BigDecimal qtyDefault, BigDecimal qtyUsed) {}

    /**
     * Kiểm phụ kiện bếp gửi và dựng danh sách cần trừ — gọi TRƯỚC khi đổi dữ liệu để lỗi (400) không để lại gì.
     * Mẫu không gửi {@code accessories} → mặc định (bộ phụ kiện của mẫu × số bánh). Gửi {@code []} → mẫu không dùng phụ kiện.
     */
    @Transactional(readOnly = true)
    public List<AccessoryPlan> planAccessories(Map<UUID, BigDecimal> produced, List<DesignQtyRequest> designs) {
        List<AccessoryPlan> plan = new ArrayList<>();
        if (produced == null || produced.isEmpty()) return plan;
        Map<UUID, DesignQtyRequest> requested = new HashMap<>();
        if (designs != null) designs.forEach(d -> { if (d != null && d.designId() != null) requested.put(d.designId(), d); });

        for (Map.Entry<UUID, BigDecimal> en : produced.entrySet()) {
            CakeDesign design = designRepository.findById(en.getKey()).orElseThrow();
            BigDecimal cakes = en.getValue();
            Map<UUID, BigDecimal> defaults = new LinkedHashMap<>();
            Map<UUID, Item> itemsById = new HashMap<>();
            for (CakeDesignAccessory acc : design.getAccessories()) {
                defaults.merge(acc.getItem().getId(), cakes.multiply(acc.getQty()), BigDecimal::add);
                itemsById.put(acc.getItem().getId(), acc.getItem());
            }
            List<DesignQtyRequest.AccessoryQty> sent = requested.get(en.getKey()) != null
                    ? requested.get(en.getKey()).accessories() : null;
            if (sent == null) {
                defaults.forEach((itemId, qty) -> plan.add(new AccessoryPlan(design.getId(), design.getCode(),
                        itemsById.get(itemId), qty, qty)));
                continue;
            }
            Set<UUID> seen = new HashSet<>();
            for (DesignQtyRequest.AccessoryQty a : sent) {
                if (a == null || a.itemId() == null) {
                    throw new IllegalArgumentException("Mẫu " + design.getCode() + ": phụ kiện thiếu itemId");
                }
                Item item = itemsById.get(a.itemId());
                if (item == null) {
                    item = itemRepository.findById(a.itemId()).orElseThrow(() -> new IllegalArgumentException(
                            "Mẫu " + design.getCode() + ": không tìm thấy phụ kiện " + a.itemId()));
                }
                String label = item.getName() + " (" + item.getCode() + ")";
                if (a.qty() == null || a.qty().signum() <= 0) {
                    throw new IllegalArgumentException("Mẫu " + design.getCode() + ", phụ kiện " + label
                            + ": số lượng phải lớn hơn 0");
                }
                if (!seen.add(item.getId())) {
                    throw new IllegalArgumentException("Mẫu " + design.getCode() + ": phụ kiện " + label + " bị trùng");
                }
                plan.add(new AccessoryPlan(design.getId(), design.getCode(), item,
                        defaults.getOrDefault(item.getId(), BigDecimal.ZERO), a.qty()));
            }
        }
        // thành phẩm không được làm phụ kiện — kiểm một lần cho cả danh sách
        Set<UUID> products = new HashSet<>(itemRepository.findProductIdsIn(
                plan.stream().map(p -> p.item().getId()).collect(Collectors.toSet())));
        for (AccessoryPlan p : plan) {
            if (products.contains(p.item().getId())) {
                throw new IllegalArgumentException("Mẫu " + p.designCode() + ": " + p.item().getName()
                        + " (" + p.item().getCode() + ") là thành phẩm, không dùng làm phụ kiện");
            }
        }
        return plan;
    }

    /**
     * Ghi phụ kiện bếp dùng và trừ kho khu sản xuất (KITCHEN + COLD) theo danh sách đã kiểm; thiếu tồn thì trừ phần có,
     * không âm, không ném lỗi, gom cảnh báo theo mặt hàng. Đánh dấu {@code accessories_deducted} để cửa hàng xác nhận không trừ lại.
     */
    @Transactional
    public List<String> applyAccessories(DeliveryRecord dr, boolean usesDesigns, List<AccessoryPlan> plan, LocalDate productionDate) {
        if (!usesDesigns) return List.of();
        dr.setAccessoriesDeducted(true);
        Map<UUID, BigDecimal> need = new LinkedHashMap<>();
        Map<UUID, BigDecimal> got = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        for (AccessoryPlan p : plan) {
            DeliveryRecordAccessory row = new DeliveryRecordAccessory();
            row.setDeliveryRecord(dr);
            row.setCakeDesign(designRepository.getReferenceById(p.designId()));
            row.setItem(p.item());
            row.setQtyDefault(p.qtyDefault());
            row.setQtyUsed(p.qtyUsed());
            row.setUnit(p.item().getUnit());
            accessoryRepository.save(row);

            UUID itemId = p.item().getId();
            names.put(itemId, p.item().getName());
            need.merge(itemId, p.qtyUsed(), BigDecimal::add);
            BigDecimal short_ = deductFromZone(itemId, p.qtyUsed(), dr.getId(),
                    "Phụ kiện mẫu " + p.designCode() + " – hoàn thành " + productionDate);
            got.merge(itemId, p.qtyUsed().subtract(short_), BigDecimal::add);
        }
        return shortageWarnings(need, got, names);
    }

    /** Trừ phụ kiện theo quy tắc chung 22/§5.2 (nhóm 1 trước, thiếu mới sang nhóm 2); trả phần còn thiếu, không bao giờ âm tồn. */
    private BigDecimal deductFromZone(UUID itemId, BigDecimal need, UUID refId, String note) {
        return productionStock.deduct(itemId, need, REF_TYPE, refId, note);
    }

    private static List<String> shortageWarnings(Map<UUID, BigDecimal> need, Map<UUID, BigDecimal> got, Map<UUID, String> names) {
        List<String> warnings = new ArrayList<>();
        need.forEach((itemId, n) -> {
            BigDecimal g = got.getOrDefault(itemId, BigDecimal.ZERO);
            if (g.compareTo(n) < 0) {
                warnings.add(names.get(itemId) + ": cần " + DesignSplit.plain(n)
                        + ", tồn " + DesignSplit.plain(g) + ", thiếu " + DesignSplit.plain(n.subtract(g)));
            }
        });
        return warnings;
    }

    // ── BE-3: cửa hàng xác nhận ──────────────────────────────

    /**
     * Ghi số nhận theo mẫu, đặt cờ lệch mẫu, ghi activity log khi lệch và trừ phụ kiện theo mẫu cửa hàng.
     * Delivery record không có dòng mẫu thì bỏ qua (trả cảnh báo rỗng).
     */
    @Transactional
    public ConfirmResult confirm(DeliveryRecord dr, UUID productId, String productLabel,
            LocalDate productionDate, BigDecimal qtyReceived, List<DesignQtyRequest> designs) {
        List<DeliveryRecordDesign> rows = new ArrayList<>(rowRepository.findByDeliveryRecordIds(List.of(dr.getId())));
        if (rows.isEmpty()) return new ConfirmResult(List.of());

        Set<UUID> inRecord = rows.stream().map(r -> r.getCakeDesign().getId()).collect(Collectors.toSet());
        Set<UUID> allowed = new HashSet<>(inRecord);
        designRepository.findAllByProduct(productId).stream().filter(CakeDesign::isActive)
                .forEach(d -> allowed.add(d.getId()));
        Map<UUID, BigDecimal> received = DesignSplit.validate(designs, allowed, qtyReceived, "thực nhận", this::codeOf);

        Map<UUID, DeliveryRecordDesign> byDesign = new LinkedHashMap<>();
        for (DeliveryRecordDesign r : rows) {
            r.setQtyReceived(received.getOrDefault(r.getCakeDesign().getId(), BigDecimal.ZERO));
            byDesign.put(r.getCakeDesign().getId(), r);
        }
        for (Map.Entry<UUID, BigDecimal> en : received.entrySet()) {
            if (byDesign.containsKey(en.getKey())) continue;
            DeliveryRecordDesign row = new DeliveryRecordDesign();   // cửa hàng nhận mẫu bếp không khai báo
            row.setDeliveryRecord(dr);
            row.setCakeDesign(designRepository.getReferenceById(en.getKey()));
            row.setQtyProduced(BigDecimal.ZERO);
            row.setQtyReceived(en.getValue());
            byDesign.put(en.getKey(), row);
        }
        rowRepository.saveAll(byDesign.values());

        boolean mismatch = isMismatch(byDesign.values());
        dr.setDesignMismatch(mismatch);
        if (mismatch) {
            String note = "Giao sai mẫu: " + mismatchText(byDesign.values());
            commandRequestRepository.save(CommandRequest.builder()
                    .entityName("DeliveryRecord")
                    .entityId(dr.getId())
                    .action(CommandAction.CONFIRM)
                    .actor(actorResolver.currentUserId())
                    .actorName(actorResolver.currentUsername())
                    .entityLabel("Giao nhận " + productLabel)
                    .note(note.length() <= 500 ? note : note.substring(0, 500))
                    .status(CommandStatus.SUCCESS)
                    .createdAt(Instant.now())
                    .build());
        }
        // 02/BE-13: phụ kiện đã trừ lúc bếp Hoàn thành thì cửa hàng không trừ nữa; phiếu cũ (chưa có cờ) vẫn trừ theo mẫu nhận
        if (dr.isAccessoriesDeducted()) return new ConfirmResult(List.of());
        return new ConfirmResult(deductAccessories(dr, byDesign.values(), productionDate));
    }

    /** Phiếu hoàn thành trước BE-13: trừ phụ kiện theo mẫu cửa hàng nhận, FIFO trong khu sản xuất. Thiếu thì trừ phần có và cảnh báo. */
    private List<String> deductAccessories(DeliveryRecord dr, Collection<DeliveryRecordDesign> rows, LocalDate date) {
        Map<UUID, BigDecimal> needByItem = new LinkedHashMap<>();
        Map<UUID, BigDecimal> gotByItem = new HashMap<>();
        Map<UUID, String> nameByItem = new HashMap<>();
        for (DeliveryRecordDesign row : rows) {
            BigDecimal qty = row.getQtyReceived();
            if (qty == null || qty.signum() <= 0) continue;
            CakeDesign design = row.getCakeDesign();
            for (CakeDesignAccessory acc : design.getAccessories()) {
                UUID itemId = acc.getItem().getId();
                BigDecimal need = qty.multiply(acc.getQty());
                nameByItem.put(itemId, acc.getItem().getName());
                needByItem.merge(itemId, need, BigDecimal::add);
                BigDecimal short_ = deductFromZone(itemId, need, dr.getId(), "Phụ kiện mẫu " + design.getCode() + " – giao " + date);
                gotByItem.merge(itemId, need.subtract(short_), BigDecimal::add);
            }
        }
        return shortageWarnings(needByItem, gotByItem, nameByItem);
    }

    /** Mã mẫu theo id, null nếu không có (dùng cho thông báo lỗi, 02/BE-10). */
    private String codeOf(UUID id) {
        return designRepository.findById(id).map(CakeDesign::getCode).orElse(null);
    }

    // ── Mismatch ─────────────────────────────────────────────

    public static boolean isMismatch(Collection<DeliveryRecordDesign> rows) {
        for (DeliveryRecordDesign r : rows) {
            BigDecimal p = r.getQtyProduced() != null ? r.getQtyProduced() : BigDecimal.ZERO;
            BigDecimal q = r.getQtyReceived() != null ? r.getQtyReceived() : BigDecimal.ZERO;
            if (q.compareTo(p) > 0) return true;   // D5: chỉ khi nhận NHIỀU HƠN bếp khai; nhận ít hơn không phải sai mẫu
        }
        return false;
    }

    /** "bếp SN16×2, XD16×1; cửa hàng SN16×1, XD16×2" (theo mã mẫu). */
    public static String mismatchText(Collection<DeliveryRecordDesign> rows) {
        List<DeliveryRecordDesign> sorted = rows.stream()
                .sorted(Comparator.comparing(r -> r.getCakeDesign().getCode())).toList();
        return "bếp " + side(sorted, true) + "; cửa hàng " + side(sorted, false);
    }

    private static String side(List<DeliveryRecordDesign> rows, boolean kitchen) {
        String s = rows.stream()
                .filter(r -> {
                    BigDecimal q = kitchen ? r.getQtyProduced() : r.getQtyReceived();
                    return q != null && q.signum() > 0;
                })
                .map(r -> r.getCakeDesign().getCode() + "×"
                        + DesignSplit.plain(kitchen ? r.getQtyProduced() : r.getQtyReceived()))
                .collect(Collectors.joining(", "));
        return s.isEmpty() ? "—" : s;
    }

    /** Cảnh báo chốt ngày: mỗi phiếu giao nhận cùng ngày có design_mismatch = true. */
    @Transactional(readOnly = true)
    public List<String> mismatchWarnings(LocalDate date) {
        Map<UUID, List<DeliveryRecordDesign>> byRecord = new LinkedHashMap<>();
        for (DeliveryRecordDesign x : rowRepository.findByProductionDate(date)) {
            if (x.getDeliveryRecord().isDesignMismatch()) {
                byRecord.computeIfAbsent(x.getDeliveryRecord().getId(), k -> new ArrayList<>()).add(x);
            }
        }
        return byRecord.values().stream()
                .map(rows -> "Giao sai mẫu: " + rows.get(0).getCakeDesign().getProduct().getName()
                        + " – " + mismatchText(rows))
                .toList();
    }

    // ── Đọc ──────────────────────────────────────────────────

    /** Phụ kiện bếp dùng theo từng phiếu giao nhận (02/BE-13), một query cho nhiều phiếu. */
    @Transactional(readOnly = true)
    public Map<UUID, List<DeliveryRecordAccessoryResponse>> accessoriesFor(Collection<UUID> deliveryRecordIds) {
        if (deliveryRecordIds.isEmpty()) return Map.of();
        Map<UUID, List<DeliveryRecordAccessoryResponse>> out = new HashMap<>();
        accessoryRepository.findByDeliveryRecordIds(deliveryRecordIds).stream()
                .sorted(Comparator.comparing((DeliveryRecordAccessory a) -> a.getCakeDesign() != null ? a.getCakeDesign().getCode() : "")
                        .thenComparing(a -> a.getItem().getCode()))
                .forEach(a -> out.computeIfAbsent(a.getDeliveryRecord().getId(), k -> new ArrayList<>())
                        .add(new DeliveryRecordAccessoryResponse(
                                a.getCakeDesign() != null ? a.getCakeDesign().getId() : null,
                                a.getCakeDesign() != null ? new ReferenceValue(a.getCakeDesign().getCode(), a.getCakeDesign().getName()) : null,
                                a.getItem().getId(), new ReferenceValue(a.getItem().getCode(), a.getItem().getName()),
                                a.getUnit(), a.getQtyDefault(), a.getQtyUsed())));
        return out;
    }

    /** Mẫu theo từng phiếu giao nhận, một query cho nhiều phiếu. Phiếu không dùng mẫu → không có key. */
    @Transactional(readOnly = true)
    public Map<UUID, List<DeliveryRecordDesignResponse>> responsesFor(Collection<UUID> deliveryRecordIds) {
        if (deliveryRecordIds.isEmpty()) return Map.of();
        Map<UUID, List<DeliveryRecordDesignResponse>> out = new HashMap<>();
        rowRepository.findByDeliveryRecordIds(deliveryRecordIds).stream()
                .sorted(Comparator.comparing(r -> r.getCakeDesign().getCode()))
                .forEach(r -> {
                    DeliveryRecordDesignResponse d = new DeliveryRecordDesignResponse();
                    d.setDesignId(r.getCakeDesign().getId());
                    d.setDesign(new ReferenceValue(r.getCakeDesign().getCode(), r.getCakeDesign().getName()));
                    d.setImageUrl(r.getCakeDesign().getImageUrl());
                    d.setQtyProduced(r.getQtyProduced());
                    d.setQtyReceived(r.getQtyReceived());
                    out.computeIfAbsent(r.getDeliveryRecord().getId(), k -> new ArrayList<>()).add(d);
                });
        return out;
    }
}
