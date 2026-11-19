package com.bakery.api.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.master.util.ExCodeDecoder;
import com.bakery.api.pricing.entity.ProductPrice;
import com.bakery.api.pricing.repository.ProductPriceRepository;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.production.service.ProductionPlannerService;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.entity.PosDailySale;
import com.bakery.api.report.repository.DailyReportLineRepository;
import com.bakery.api.report.repository.DailyReportRepository;
import com.bakery.api.report.repository.PosDailySaleRepository;
import com.bakery.api.cake.service.DailyReportDesignService.DesignRow;
import com.bakery.framework.entity.DailyReportStatus;
import com.bakery.framework.entity.DeliveryStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.entity.WarehouseType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý báo cáo cuối ngày.
 *
 * <p>Flow:
 * 1. getOrCreateDraft(date) → tạo DRAFT nếu chưa có
 * 2. updateRemainingQty(reportId, itemId, qty) → nhân viên nhập tay qty_remaining_actual
 * 3. finalize(reportId) → Admin chốt: tổng hợp toàn bộ số liệu + snapshot giá
 *    → Tự động trigger ProductionPlannerService.generateDraft() cho ngày hôm sau
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final DailyReportRepository reportRepository;
    private final DailyReportLineRepository lineRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final PosDailySaleRepository posSaleRepository;
    private final ProductMappingRepository productMappingRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ItemLookupRepository itemRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final BakeryActorResolver actorResolver;
    private final ProductionPlannerService productionPlannerService;
    private final com.bakery.framework.repository.CommandRequestRepository commandRequestRepository;
    private final CancelRecordService cancelRecordService;
    private final com.bakery.api.cake.service.DailyReportDesignService designService;
    private final com.bakery.api.cake.service.DeliveryDesignService deliveryDesignService;

    // ── Get / Create ─────────────────────────────────────────────

    @Transactional
    public DailyReport getOrCreateDraft(LocalDate date) {
        boolean[] isNew = {false};
        DailyReport report = reportRepository.findByReportDate(date).orElseGet(() -> {
            isNew[0] = true;
            DailyReport r = new DailyReport();
            r.setReportDate(date);
            r.setStatus(DailyReportStatus.DRAFT);
            r.setCreatedBy(actorResolver.currentUserId());
            return reportRepository.save(r);
        });
        // Refresh cancel records mỗi lần init (cập nhật qty_opening/received nếu có delivery mới)
        cancelRecordService.generateForReport(report);
        return report;
    }

    public DailyReport getById(UUID id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport", id));
    }

    /** Tìm report theo ngày — không tạo mới nếu chưa có. */
    public Optional<DailyReport> findByDate(LocalDate date) {
        return reportRepository.findByReportDate(date);
    }

    public List<DailyReportLine> getLines(UUID reportId) {
        return lineRepository.findByDailyReportIdWithItem(reportId);
    }

    /** Dòng báo cáo + số liệu theo mẫu của từng thành phẩm (productId → mẫu); thành phẩm không mẫu không có key. */
    public record LinesView(List<DailyReportLine> lines, Map<UUID, List<DesignRow>> designs) {}

    /** Tồn đầu theo thành phẩm = còn lại đã nhập ở báo cáo gần nhất trước {@code date} (D13, không đòi FINALIZED, D12). */
    private Map<UUID, BigDecimal> openingByItem(LocalDate date) {
        return reportRepository.findFirstByReportDateLessThanOrderByReportDateDesc(date)
                .map(prev -> lineRepository.findByDailyReportIdWithItem(prev.getId()).stream()
                        .filter(l -> l.getItem() != null && l.getQtyRemainingActual() != null)
                        .collect(Collectors.toMap(l -> l.getItem().getId(),
                                DailyReportLine::getQtyRemainingActual, (a, b) -> a)))
                .orElse(Collections.emptyMap());
    }

    /** Báo cáo gần nhất có ngày &lt; {@code date}. */
    public Optional<DailyReport> findPrevious(LocalDate date) {
        return reportRepository.findFirstByReportDateLessThanOrderByReportDateDesc(date);
    }

    /**
     * 02/BE-9: số liệu của một ngày, tính MỘT lần và dùng chung cho {@code GET /lines} DRAFT lẫn {@code finalize}
     * (không ghi DB, không set lên entity).
     *
     * @param opening  thành phẩm → còn lại đã nhập ở báo cáo gần nhất trước D
     * @param received tổng qty_received của delivery có ngày SX = D
     * @param produced tổng qty_produced của delivery có ngày SX = D
     * @param pos      item → tổng bán POS ngày D (qua product_mapping)
     */
    private record DayBasis(List<DeliveryRecord> deliveries, Map<UUID, BigDecimal> opening,
                            Map<UUID, BigDecimal> received, Map<UUID, BigDecimal> produced,
                            List<PosDailySale> posSales, Map<UUID, BigDecimal> pos,
                            List<UUID> designedProducts, Map<UUID, List<DesignRow>> designs) {
        /** D14: đã upload POS của ngày thì qtySoldPos có số (0 nếu không bán); chưa upload → null. */
        boolean posUploaded() { return !posSales.isEmpty(); }

        /** Item cần có dòng ngoài các dòng đã lưu: tồn đầu &gt; 0 (D13), nhận &gt; 0, bán POS &gt; 0, hoặc có số theo mẫu. */
        Set<UUID> candidateItemIds() {
            Set<UUID> ids = new LinkedHashSet<>();
            opening.forEach((id, q) -> { if (q.signum() > 0) ids.add(id); });
            received.forEach((id, q) -> { if (q.signum() > 0) ids.add(id); });
            pos.forEach((id, q) -> { if (q.signum() > 0) ids.add(id); });
            ids.addAll(designs.keySet());
            return ids;
        }
    }

    private DayBasis dayBasis(DailyReport report) {
        LocalDate date = report.getReportDate();
        List<DeliveryRecord> deliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(date);
        Map<UUID, BigDecimal> received = new HashMap<>();
        Map<UUID, BigDecimal> produced = new HashMap<>();
        for (DeliveryRecord dr : deliveries) {
            if (dr.getProductionRequestLine() == null || dr.getProductionRequestLine().getProduct() == null) continue;
            UUID id = dr.getProductionRequestLine().getProduct().getId();
            produced.merge(id, dr.getQtyProduced() != null ? dr.getQtyProduced() : BigDecimal.ZERO, BigDecimal::add);
            received.merge(id, dr.getQtyReceived() != null ? dr.getQtyReceived() : BigDecimal.ZERO, BigDecimal::add);
        }
        List<PosDailySale> posSales = posSaleRepository.findBySaleDate(date);
        List<UUID> designed = designService.allDesignedProductIds();
        return new DayBasis(deliveries, openingByItem(date), received, produced, posSales,
                posQtyByItem(posSales), designed, designService.compute(report, designed));
    }

    /**
     * 02/BE-4, 02/BE-8. FINALIZED đọc từ snapshot. DRAFT tính ngay lúc đọc, không ghi DB: điền tồn đầu và
     * số nhận mới nhất vào dòng đã lưu, và thêm dòng "ảo" (id null) cho mọi thành phẩm có tồn đầu &gt; 0
     * hoặc nhận &gt; 0 hoặc có bán trên POS mà chưa có dòng; điền {@code qtySoldPos} (D14). Sắp theo tên item.
     */
    @Transactional(readOnly = true)   // readOnly: các setter dưới đây trên entity đã nạp không bao giờ được flush
    public LinesView getLinesWithDesigns(UUID reportId) {
        DailyReport report = getById(reportId);
        List<DailyReportLine> lines = new ArrayList<>(lineRepository.findByDailyReportIdWithItem(reportId));
        if (report.getStatus() == DailyReportStatus.FINALIZED) {
            return new LinesView(lines, designService.stored(report));
        }
        DayBasis b = dayBasis(report);
        Map<UUID, BigDecimal> opening = b.opening();
        Map<UUID, BigDecimal> received = b.received();
        Map<UUID, BigDecimal> posByItem = b.pos();
        boolean posUploaded = b.posUploaded();
        Map<UUID, List<DesignRow>> designs = b.designs();

        Set<UUID> have = new HashSet<>();
        for (DailyReportLine l : lines) {
            if (l.getItem() == null) continue;
            UUID id = l.getItem().getId();
            have.add(id);
            l.setQtyRemainingOpening(opening.getOrDefault(id, BigDecimal.ZERO));
            l.setQtyReceived(received.getOrDefault(id, BigDecimal.ZERO));
            l.setQtySoldPos(posUploaded ? posByItem.getOrDefault(id, BigDecimal.ZERO) : null);
        }
        Set<UUID> candidates = b.candidateItemIds();
        candidates.removeAll(have);
        if (!candidates.isEmpty()) {
            Set<UUID> productIds = new HashSet<>(itemRepository.findProductIdsIn(candidates));
            for (var item : itemRepository.findAllById(productIds)) {
                DailyReportLine v = new DailyReportLine();
                v.setDailyReport(report);
                v.setItem(item);
                v.setQtyRemainingOpening(opening.getOrDefault(item.getId(), BigDecimal.ZERO));
                v.setQtyReceived(received.getOrDefault(item.getId(), BigDecimal.ZERO));
                v.setQtySoldPos(posUploaded ? posByItem.getOrDefault(item.getId(), BigDecimal.ZERO) : null);
                lines.add(v);
            }
        }
        java.text.Collator vi = java.text.Collator.getInstance(Locale.forLanguageTag("vi"));
        lines.sort(Comparator.comparing(l -> l.getItem() != null && l.getItem().getName() != null
                ? l.getItem().getName() : "", vi));
        return new LinesView(lines, designs);
    }

    // ── Nhân viên nhập qty_remaining_actual + qty_cancelled ─────

    /**
     * Nhân viên nhập 2 ô cuối ngày: số còn lại + số đã hủy.
     * Có thể gọi bất kỳ lúc nào trước khi FINALIZED.
     * Tự upsert: tạo line nếu chưa có.
     * qty_sold_implied sẽ được tính chính xác trong finalize() (cần opening stock).
     */
    @Transactional
    public DailyReportLine updateRemainingQty(UUID reportId, UUID itemId,
            BigDecimal qtyRemainingActual, BigDecimal qtyCancelled, String note) {
        return updateRemainingQty(reportId, itemId, qtyRemainingActual, qtyCancelled, note, null);
    }

    /**
     * @param designs còn lại/hủy theo mẫu — bắt buộc khi thành phẩm có mẫu (khi đó {@code qtyRemainingActual}
     *                và {@code qtyCancelled} bị bỏ qua, dòng thành phẩm lấy tổng các mẫu).
     */
    @Transactional
    public DailyReportLine updateRemainingQty(UUID reportId, UUID itemId,
            BigDecimal qtyRemainingActual, BigDecimal qtyCancelled, String note,
            List<com.bakery.api.cake.dto.SubmitLineBody.DesignRemaining> designs) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);
        if (itemRepository.findProductIdsIn(List.of(itemId)).isEmpty()) {
            throw new IllegalStateException("Báo cáo ngày chỉ dành cho thành phẩm");
        }

        DailyReportLine line = lineRepository
                .findByDailyReportIdAndItemId(reportId, itemId)
                .orElseGet(() -> {
                    DailyReportLine l = new DailyReportLine();
                    l.setDailyReport(report);
                    l.setItem(itemRepository.getReferenceById(itemId));
                    return l;
                });

        line.setNote(note);
        line.setUpdatedAt(Instant.now());

        if (!designService.designedProductIdsAmong(List.of(itemId)).isEmpty()) {
            DailyReportLine saved = lineRepository.save(line);   // cần id cho dòng mẫu
            BigDecimal[] totals = designService.submit(saved, itemId, designs);
            saved.setQtyRemainingActual(totals[0]);
            saved.setQtyCancelled(totals[1]);
            return lineRepository.save(saved);
        }
        if (qtyRemainingActual == null) {
            throw new IllegalArgumentException("qtyRemainingActual là bắt buộc");
        }
        line.setQtyRemainingActual(qtyRemainingActual);
        if (qtyCancelled != null) {
            line.setQtyCancelled(qtyCancelled);
        }

        return lineRepository.save(line);
    }

    // ── Admin FINALIZE ───────────────────────────────────────────

    /** Kết quả chốt ngày: báo cáo + cảnh báo tồn phụ kiện (không chặn chốt, D18). */
    public record FinalizeResult(DailyReport report, List<String> stockWarnings) {}

    /**
     * Admin chốt báo cáo ngày:
     * 1. Tổng hợp qtyProduced + qtyReceived từ DeliveryRecord
     * 2. Tổng hợp qtySoldPos từ pos_daily_sale (qua product_mapping)
     * 3. Tính discrepancy 2 chiều
     * 4. Snapshot giá bán tại thời điểm chốt
     * 5. Status → FINALIZED
     */
    @Transactional
    public FinalizeResult finalize(UUID reportId) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);

        LocalDate date = report.getReportDate();

        // ── Bước 1+2+3b: số liệu của ngày (giao nhận, POS, tồn đầu, mẫu) — CÙNG hàm với GET /lines (02/BE-9) ──
        DayBasis basis = dayBasis(report);
        List<DeliveryRecord> deliveries = basis.deliveries();
        Map<UUID, BigDecimal> posByItem = basis.pos();
        List<PosDailySale> posSales = basis.posSales();
        List<UUID> designedProducts = basis.designedProducts();
        Map<UUID, List<DesignRow>> designsByProduct = basis.designs();
        Map<UUID, BigDecimal> openingByItem = basis.opening();

        // Tập dòng = dòng đã lưu ∪ PRODUCT có tồn đầu/nhận/POS (như GET /lines) ∪ thành phẩm có phiếu giao (giữ chênh lệch bếp)
        Set<UUID> itemIds = new LinkedHashSet<>();
        lineRepository.findByDailyReportIdWithItem(reportId).forEach(l -> {
            if (l.getItem() != null) itemIds.add(l.getItem().getId());
        });
        Set<UUID> extra = basis.candidateItemIds();
        itemIds.forEach(extra::remove);
        if (!extra.isEmpty()) itemIds.addAll(itemRepository.findProductIdsIn(extra));
        itemIds.addAll(basis.produced().keySet());

        // ── Bước 3: Load expiry config + tồn SHOP ───────────────────
        Map<UUID, Integer> shelfDaysByItem = expiryConfigRepository.findAll().stream()
                .filter(c -> c.getShelfDays() != null && c.getItem() != null)
                .collect(Collectors.toMap(
                        c -> c.getItem().getId(),
                        ProductExpiryConfig::getShelfDays,
                        (a, b) -> a));

        // Tồn SHOP trước khi NV huỷ = qty_system_cancel
        List<Warehouse> finShopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        final UUID finShopWarehouseId = finShopWarehouses.isEmpty() ? null : finShopWarehouses.get(0).getId();
        final Map<UUID, BigDecimal> shopStockByItem = finShopWarehouseId == null
                ? Collections.emptyMap()
                : stockLotRepository.findAll().stream()
                        .filter(l -> finShopWarehouseId.equals(
                                l.getWarehouse() != null ? l.getWarehouse().getId() : null)
                                && l.getQtyRemaining() != null
                                && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0
                                && l.getItem() != null)
                        .collect(Collectors.groupingBy(
                                l -> l.getItem().getId(),
                                Collectors.reducing(BigDecimal.ZERO,
                                        l -> l.getQtyRemaining(), BigDecimal::add)));

        // ── Bước 4: Upsert DailyReportLine cho từng item ─────────
        for (UUID itemId : itemIds) {
            BigDecimal qtyProduced = basis.produced().getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal qtyReceived = basis.received().getOrDefault(itemId, BigDecimal.ZERO);

            DailyReportLine line = lineRepository
                    .findByDailyReportIdAndItemId(reportId, itemId)
                    .orElseGet(() -> {
                        DailyReportLine l = new DailyReportLine();
                        l.setDailyReport(report);
                        l.setItem(itemRepository.getReferenceById(itemId));
                        return l;
                    });

            line.setQtyProduced(qtyProduced);
            line.setQtyReceived(qtyReceived);

            // Bánh có mẫu: còn lại/hủy của dòng thành phẩm = tổng các mẫu
            List<DesignRow> designRows = designsByProduct.getOrDefault(itemId, List.of());
            if (designRows.stream().anyMatch(r -> r.remaining() != null)) {
                line.setQtyRemainingActual(designRows.stream().map(DesignRow::remaining)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
                line.setQtyCancelled(designRows.stream().map(DesignRow::cancelled)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            }

            // Opening stock (tồn đầu ngày từ ngày hôm trước)
            BigDecimal qtyRemainingOpening = openingByItem.getOrDefault(itemId, BigDecimal.ZERO);
            line.setQtyRemainingOpening(qtyRemainingOpening);

            // POS qty
            BigDecimal qtySoldPos = posByItem.getOrDefault(itemId, BigDecimal.ZERO);
            line.setQtySoldPos(qtySoldPos);

            // System cancel: tồn SHOP của items hết HSD hôm nay
            BigDecimal qtySystemCancel = BigDecimal.ZERO;
            Integer shelfDays = shelfDaysByItem.get(itemId);
            if (shelfDays != null) {
                boolean hasExpiringBatch = deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .anyMatch(dr -> {
                            LocalDate pd = dr.getProductionRequestLine()
                                    .getProductionRequest().getProductionDate();
                            return !pd.plusDays(shelfDays).isAfter(date);
                        });
                if (hasExpiringBatch) {
                    qtySystemCancel = shopStockByItem.getOrDefault(itemId, BigDecimal.ZERO);
                }
            }
            line.setQtySystemCancel(qtySystemCancel);

            // Còn lại HT = opening + received - sold_pos - system_cancel
            BigDecimal qtySystemRemaining = qtyRemainingOpening.add(qtyReceived)
                    .subtract(qtySoldPos).subtract(qtySystemCancel);
            line.setQtySystemRemaining(qtySystemRemaining);

            // Chênh lệch bếp (qty_produced - qty_received)
            line.setDiscrepancyKitchen(qtyProduced.subtract(qtyReceived));

            // qty_sold_implied = opening + received - remaining - cancelled (D7 mới, 02/BE-11: hủy là mất, không phải bán)
            // — cùng hàm với số bán theo mẫu. discrepancy_pos = sold_implied - sold_pos
            if (line.getQtyRemainingActual() != null) {
                BigDecimal qtySoldImplied = com.bakery.api.cake.service.DailyReportDesignService.soldImplied(
                        qtyRemainingOpening, qtyReceived, line.getQtyRemainingActual(), line.getQtyCancelled());
                line.setQtySoldImplied(qtySoldImplied);
                line.setDiscrepancyPos(qtySoldImplied.subtract(qtySoldPos));
            }

            // discrepancy_remaining = remaining_actual - system_remaining
            if (line.getQtyRemainingActual() != null) {
                line.setDiscrepancyRemaining(line.getQtyRemainingActual().subtract(qtySystemRemaining));
            }

            // discrepancy_cancel = cancelled_nv - system_cancel
            if (line.getQtyCancelled() != null && qtySystemCancel.compareTo(BigDecimal.ZERO) > 0) {
                line.setDiscrepancyCancel(line.getQtyCancelled().subtract(qtySystemCancel));
            }

            // Snapshot giá bán mới nhất
            List<ProductPrice> prices = productPriceRepository.findByItemIdOrderByEffectiveDateDesc(itemId);
            if (!prices.isEmpty()) {
                line.setSellingPrice(prices.get(0).getPrice());
            }

            line.setUpdatedAt(Instant.now());
            DailyReportLine savedLine = lineRepository.save(line);
            if (!designRows.isEmpty()) designService.snapshot(savedLine, designRows);
        }

        // ── Bước 4: FINALIZE ─────────────────────────────────────
        report.setStatus(DailyReportStatus.FINALIZED);
        report.setFinalizedAt(Instant.now());
        report.setFinalizedBy(actorResolver.currentUserId());
        report.setUpdatedAt(Instant.now());
        DailyReport saved = reportRepository.save(report);

        // ── Bước 4b: Trừ tồn phụ kiện (không phải PRODUCT) theo POS ─────
        // Không bọc try/catch: lỗi khác ngoài thiếu tồn thì cả finalize rollback, tránh trừ nửa chừng.
        List<String> stockWarnings = new ArrayList<>(deductPosAccessories(saved, date, posByItem));

        // 02/BE-5: nến tặng 0đ vượt số bánh kem bán; giao sai mẫu (không chặn)
        String gift = giftCandleWarning(posSales, posByItem, designedProducts);
        if (gift != null) stockWarnings.add(gift);
        stockWarnings.addAll(deliveryDesignService.mismatchWarnings(date));

        // Log finalize activity
        try {
            commandRequestRepository.save(com.bakery.framework.entity.CommandRequest.builder()
                    .entityName("DailyReport")
                    .entityId(saved.getId())
                    .action(com.bakery.framework.entity.CommandAction.FINALIZE)
                    .actor(actorResolver.currentUserId())
                    .actorName(actorResolver.currentUsername())
                    .entityLabel("Báo cáo ngày " + date)
                    .note(stockWarnings.isEmpty() ? null : truncate("Cảnh báo tồn: " + String.join("; ", stockWarnings), 500))
                    .status(com.bakery.framework.entity.CommandStatus.SUCCESS)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Lỗi log FINALIZE: {}", ex.getMessage());
        }

        // ── Bước 5: Cập nhật tồn kho SHOP ────────────────────────
        try {
            updateShopStock(saved, date);
        } catch (Exception ex) {
            log.error("Lỗi cập nhật tồn kho SHOP sau finalize {}: {}", saved.getId(), ex.getMessage(), ex);
        }

        // ── Bước 6: Tự động tạo kế hoạch SX ngày mai ────────────
        try {
            productionPlannerService.generateDraft(saved);
            log.info("Đã tạo kế hoạch SX cho ngày {}", saved.getReportDate().plusDays(1));
        } catch (Exception ex) {
            // Không để lỗi planner rollback finalize — log và tiếp tục
            log.error("Lỗi tạo kế hoạch SX sau finalize báo cáo {}: {}", saved.getId(), ex.getMessage(), ex);
        }

        return new FinalizeResult(saved, stockWarnings);
    }

    /** EX_CODE → item id qua product_mapping (EX_CODE chưa map thì không có key). */
    private Map<String, UUID> itemsByExCode(List<PosDailySale> posSales) {
        Map<String, UUID> m = new HashMap<>();
        for (PosDailySale ps : posSales) {
            if (m.containsKey(ps.getExCode())) continue;
            productMappingRepository.findItemIdByExCode(ps.getExCode()).ifPresent(id -> m.put(ps.getExCode(), id));
        }
        return m;
    }

    /**
     * 02/D8: tổng nến tặng (item giftWithCake, dòng POS 0đ) &gt; tổng bánh kem bán (thành phẩm có mẫu)
     * thì trả cảnh báo, ngược lại null. Không chặn chốt ngày.
     */
    private String giftCandleWarning(List<PosDailySale> posSales, Map<UUID, BigDecimal> posByItem, Collection<UUID> designedProducts) {
        BigDecimal cakes = designedProducts.stream().map(id -> posByItem.getOrDefault(id, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, UUID> itemByExCode = itemsByExCode(posSales);
        Map<UUID, BigDecimal> zeroByItem = new HashMap<>();
        for (PosDailySale ps : posSales) {
            UUID itemId = itemByExCode.get(ps.getExCode());
            if (itemId != null && ps.getTotalAmount() != null && ps.getTotalAmount().signum() == 0) {
                zeroByItem.merge(itemId, ps.getQtySold() != null ? ps.getQtySold() : BigDecimal.ZERO, BigDecimal::add);
            }
        }
        if (zeroByItem.isEmpty()) return null;
        BigDecimal gifts = itemRepository.findAllById(zeroByItem.keySet()).stream()
                .filter(com.bakery.api.master.entity.Item::isGiftWithCake)
                .map(i -> zeroByItem.get(i.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gifts.compareTo(cakes) <= 0) return null;
        return "Nến tặng 0đ: " + fmt(gifts) + ", bánh kem bán " + fmt(cakes) + ", vượt " + fmt(gifts.subtract(cakes));
    }

    /** Cộng {@code qty_sold} theo item qua product_mapping (kể cả dòng 0đ). EX_CODE chưa map thì bỏ qua. */
    private Map<UUID, BigDecimal> posQtyByItem(List<PosDailySale> posSales) {
        Map<String, UUID> itemByExCode = itemsByExCode(posSales);
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (PosDailySale ps : posSales) {
            UUID itemId = itemByExCode.get(ps.getExCode());
            if (itemId != null) {
                result.merge(itemId, ps.getQtySold() != null ? ps.getQtySold() : BigDecimal.ZERO, BigDecimal::add);
            }
        }
        return result;
    }

    /**
     * D17/D18: trừ FIFO lô tại kho Cửa Hàng cho item không phải PRODUCT theo số POS bán trong ngày.
     * Thiếu tồn → trừ hết phần có, không âm, thêm cảnh báo.
     */
    private List<String> deductPosAccessories(DailyReport report, LocalDate date, Map<UUID, BigDecimal> posByItem) {
        if (posByItem.isEmpty()) return List.of();
        Set<UUID> productIds = new HashSet<>(itemRepository.findProductIdsIn(posByItem.keySet()));
        List<UUID> accessoryIds = posByItem.keySet().stream().filter(id -> !productIds.contains(id)).toList();
        if (accessoryIds.isEmpty()) return List.of();

        List<Warehouse> shops = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        Warehouse shop = shops.isEmpty() ? null : shops.get(0);
        Map<UUID, String> nameById = itemRepository.findAllById(accessoryIds).stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> i.getName()));

        List<String> warnings = new ArrayList<>();
        for (UUID itemId : accessoryIds) {
            BigDecimal sold = posByItem.get(itemId);
            if (sold.signum() <= 0) continue;
            List<StockLot> lots = shop == null ? List.of()
                    : stockLotRepository.findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            itemId, shop.getId(), BigDecimal.ZERO);
            BigDecimal available = lots.stream().map(StockLot::getQtyRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shortfall = deductFifo(lots, sold, (lot, qty) -> {
                StockMovement out = new StockMovement();
                out.setLot(lot);
                out.setMovementType(MovementType.OUT);
                out.setQty(qty);
                out.setRefType("POS_SALE");
                out.setRefId(report.getId());
                out.setNote("Bán POS ngày " + date);
                stockMovementRepository.save(out);
                stockLotRepository.save(lot);
            });
            if (shortfall.signum() > 0) {
                warnings.add(nameById.getOrDefault(itemId, itemId.toString()) + ": POS bán " + fmt(sold)
                        + ", tồn " + fmt(available) + ", thiếu " + fmt(shortfall));
            }
        }
        return warnings;
    }

    /** Xem {@link com.bakery.api.inventory.service.StockFifo#deduct}. */
    static BigDecimal deductFifo(List<StockLot> lots, BigDecimal qty, java.util.function.BiConsumer<StockLot, BigDecimal> onDeduct) {
        return com.bakery.api.inventory.service.StockFifo.deduct(lots, qty, onDeduct);
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String truncate(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }

    /**
     * Danh sách bánh cần hủy cho ngày {@code reportDate}.
     *
     * <p>Logic:
     * <ol>
     *   <li>Lấy tất cả delivery records trong vòng 14 ngày trước reportDate
     *       (bao gồm cả hôm nay) — giới hạn bởi max shelf_days thực tế.</li>
     *   <li>Với mỗi item, lấy các production_date thực tế từ ProductionRequest.</li>
     *   <li>Lấy shelf_days từ ProductExpiryConfig. Nếu item không có config → bỏ qua.</li>
     *   <li>Nếu production_date + shelf_days ≤ reportDate → hết hạn, cần hủy.</li>
     *   <li>Attach qty_remaining_actual + qty_cancelled từ DailyReportLine hôm nay.</li>
     * </ol>
     *
     * <p>Ví dụ: bánh sản xuất thứ 7 (production_date = Sat), shelf_days = 2
     * → expiry = Mon → xuất hiện trong cancel list thứ 2.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCancelList(UUID reportId) {
        DailyReport report = getById(reportId);
        LocalDate reportDate = report.getReportDate();

        // Cửa sổ tìm kiếm: 14 ngày (đủ bao trùm mọi shelf_days thực tế)
        LocalDate lookback = reportDate.minusDays(14);

        // Tải tồn kho SHOP một lần — dùng để tính qtySystemCancel (có ngay sau POS upload)
        List<Warehouse> shopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        final UUID shopWarehouseId = shopWarehouses.isEmpty() ? null : shopWarehouses.get(0).getId();
        // itemId → tổng qty_remaining trong SHOP
        Map<UUID, BigDecimal> shopStockByItem = shopWarehouseId == null
                ? Collections.emptyMap()
                : stockLotRepository.findAll().stream()
                        .filter(l -> shopWarehouseId.equals(
                                l.getWarehouse() != null ? l.getWarehouse().getId() : null)
                                && l.getQtyRemaining() != null
                                && l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0
                                && l.getItem() != null)
                        .collect(Collectors.groupingBy(
                                l -> l.getItem().getId(),
                                Collectors.reducing(BigDecimal.ZERO,
                                        l -> l.getQtyRemaining(), BigDecimal::add)));

        // Tất cả delivery records trong 14 ngày → map itemId → set(productionDate)
        List<DeliveryRecord> deliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDateBetween(lookback, reportDate);

        // group: itemId → set production dates (chỉ lấy records có product)
        Map<UUID, Set<LocalDate>> prodDatesByItem = deliveries.stream()
                .filter(dr -> dr.getProductionRequestLine() != null
                        && dr.getProductionRequestLine().getProduct() != null)
                .collect(Collectors.groupingBy(
                        dr -> dr.getProductionRequestLine().getProduct().getId(),
                        Collectors.mapping(
                                dr -> dr.getProductionRequestLine()
                                        .getProductionRequest().getProductionDate(),
                                Collectors.toSet())));

        if (prodDatesByItem.isEmpty()) return List.of();

        // Load expiry configs cho tất cả items một lần
        Map<UUID, Integer> shelfDaysByItem = expiryConfigRepository.findAll().stream()
                .filter(c -> c.getShelfDays() != null && c.getItem() != null)
                .collect(Collectors.toMap(
                        c -> c.getItem().getId(),
                        ProductExpiryConfig::getShelfDays,
                        (a, b) -> a));

        // Load today's report lines một lần
        Map<UUID, DailyReportLine> lineByItem = lineRepository
                .findByDailyReportIdWithItem(reportId).stream()
                .filter(l -> l.getItem() != null)
                .collect(Collectors.toMap(l -> l.getItem().getId(), l -> l, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<UUID, Set<LocalDate>> entry : prodDatesByItem.entrySet()) {
            UUID itemId = entry.getKey();
            Integer shelfDays = shelfDaysByItem.get(itemId);
            if (shelfDays == null) continue; // không có config → không quản lý HSD

            // Kiểm tra xem có production batch nào hết hạn vào/trước reportDate không
            boolean expiring = entry.getValue().stream()
                    .anyMatch(pd -> !pd.plusDays(shelfDays).isAfter(reportDate));
            if (!expiring) continue;

            // Lấy production dates đang hết hạn (để hiển thị thông tin)
            List<LocalDate> expiringDates = entry.getValue().stream()
                    .filter(pd -> !pd.plusDays(shelfDays).isAfter(reportDate))
                    .sorted()
                    .toList();

            // Lấy item info từ line hoặc repository
            DailyReportLine line = lineByItem.get(itemId);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", itemId);

            // EX_CODEs hết hạn: lọc theo dayChar khớp ngày SX đang hết hạn
            // Lấy groupCode từ itemGroup của item (prefix EX_CODE)
            String groupCode = null;
            if (line != null && line.getItem() != null
                    && line.getItem().getItemGroup() != null) {
                groupCode = line.getItem().getItemGroup().getCode();
            }
            if (groupCode == null) {
                // fallback: lấy từ delivery record
                groupCode = deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .findFirst()
                        .map(dr -> dr.getProductionRequestLine().getProduct().getItemGroup())
                        .filter(g -> g != null)
                        .map(g -> g.getCode())
                        .orElse(null);
            }
            final String finalGroupCode = groupCode;
            List<String> allMappings = productMappingRepository.findByItemId(itemId).stream()
                    .map(ProductMapping::getExCode)
                    .sorted()
                    .toList();
            row.put("allExCodes", allMappings);

            List<String> expiringExCodes = allMappings.stream()
                    .filter(ec -> {
                        if (finalGroupCode == null) return false;
                        return expiringDates.stream().anyMatch(pd ->
                                ExCodeDecoder.matchesProductionDate(ec, finalGroupCode, pd));
                    })
                    .toList();
            row.put("expiringExCodes", expiringExCodes);

            // Item name/code từ line nếu có, fallback sang getReferenceById
            if (line != null && line.getItem() != null) {
                row.put("itemCode", line.getItem().getCode() != null ? line.getItem().getCode() : "");
                row.put("itemName", line.getItem().getName() != null ? line.getItem().getName() : "");
            } else {
                // Lấy từ delivery records (product đã load từ query)
                deliveries.stream()
                        .filter(dr -> dr.getProductionRequestLine() != null
                                && dr.getProductionRequestLine().getProduct() != null
                                && itemId.equals(dr.getProductionRequestLine().getProduct().getId()))
                        .findFirst()
                        .ifPresent(dr -> {
                            var p = dr.getProductionRequestLine().getProduct();
                            row.put("itemCode", p.getCode() != null ? p.getCode() : "");
                            row.put("itemName", p.getName() != null ? p.getName() : "");
                        });
            }

            row.put("shelfDays", shelfDays);
            row.put("expiringProductionDates", expiringDates);

            // qtySystemCancel = tồn kho SHOP hiện tại cho item này
            // Có ngay sau POS upload, KHÔNG cần NV nhập tồn cuối ngày trước
            BigDecimal qtySystemCancel = shopStockByItem.getOrDefault(itemId, BigDecimal.ZERO);
            row.put("qtySystemCancel", qtySystemCancel.compareTo(BigDecimal.ZERO) > 0 ? qtySystemCancel : null);

            if (line != null) {
                BigDecimal qtyRemainingActual = line.getQtyRemainingActual();
                BigDecimal qtyCancelled       = line.getQtyCancelled();
                row.put("qtyReceived",      line.getQtyReceived());
                row.put("qtyCancelled",     qtyCancelled);
                // qtyRemainingActual: NV nhập SAU khi đã huỷ — dùng để đối chiếu
                row.put("qtyRemainingActual", qtyRemainingActual);
                // discrepancy: NV huỷ (qtyCancelled) so với HT dự kiến (qtySystemCancel)
                BigDecimal discrepancy = (qtyCancelled != null && qtySystemCancel.compareTo(BigDecimal.ZERO) > 0)
                        ? qtyCancelled.subtract(qtySystemCancel) : null;
                row.put("discrepancyCancelQty", discrepancy);
            } else {
                row.put("qtyReceived",          null);
                row.put("qtyCancelled",         null);
                row.put("qtyRemainingActual",   null);
                row.put("discrepancyCancelQty", null);
            }
            result.add(row);
        }

        // Sort: shelf_days tăng dần (bánh tươi trước), rồi theo tên
        result.sort(Comparator
                .<Map<String, Object>, Integer>comparing(r -> (Integer) r.get("shelfDays"))
                .thenComparing(r -> String.valueOf(r.getOrDefault("itemName", ""))));

        return result;
    }

    // ── Private ──────────────────────────────────────────────────

    /**
     * Sau khi finalize, cập nhật tồn kho SHOP = qty_remaining_actual của từng item.
     *
     * <p>Logic:
     * - Tìm kho SHOP (WarehouseType.SHOP). Nếu chưa có → bỏ qua.
     * - Với mỗi DailyReportLine có qty_remaining_actual:
     *   1. Zero out toàn bộ lot cũ của item tại SHOP (ghi StockMovement OUT)
     *   2. Tạo lot mới với qty = qty_remaining_actual (ghi StockMovement IN)
     *
     * <p>Idempotent: gọi lại sẽ reset về trạng thái mới nhất.
     */
    private void updateShopStock(DailyReport report, LocalDate reportDate) {
        List<Warehouse> shopWarehouses = warehouseRepository.findByWarehouseType(WarehouseType.SHOP);
        if (shopWarehouses.isEmpty()) {
            log.warn("Không tìm thấy kho SHOP, bỏ qua cập nhật tồn kho cửa hàng.");
            return;
        }
        Warehouse shopWarehouse = shopWarehouses.get(0);

        List<DailyReportLine> lines = lineRepository.findByDailyReportIdWithItem(report.getId());
        // D16: chỉ thành phẩm. Dòng cũ của hàng khác (nến...) giữ nguyên lô, HSD, giá nhập.
        Set<UUID> productIds = new HashSet<>(itemRepository.findProductIdsIn(
                lines.stream().filter(l -> l.getItem() != null).map(l -> l.getItem().getId()).toList()));
        for (DailyReportLine line : lines) {
            if (line.getQtyRemainingActual() == null || line.getItem() == null) continue;
            if (!productIds.contains(line.getItem().getId())) continue;

            UUID itemId = line.getItem().getId();

            // Bước 1: Zero out các lot cũ còn hàng tại SHOP
            List<StockLot> oldLots = stockLotRepository
                    .findByItemIdAndWarehouseId(itemId, shopWarehouse.getId())
                    .stream()
                    .filter(l -> l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            for (StockLot old : oldLots) {
                StockMovement out = new StockMovement();
                out.setLot(old);
                out.setMovementType(MovementType.OUT);
                out.setQty(old.getQtyRemaining());
                out.setRefType("DAILY_REPORT_FINALIZE");
                out.setRefId(report.getId());
                out.setNote("Chốt cuối ngày " + reportDate + " — reset tồn kho SHOP");
                stockMovementRepository.save(out);
                old.setQtyRemaining(BigDecimal.ZERO);
                stockLotRepository.save(old);
            }

            BigDecimal qtyRemaining = line.getQtyRemainingActual();
            if (qtyRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Bước 2: Tạo lot mới với tồn cuối ngày
            BigDecimal unitCost = line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO;
            StockLot newLot = new StockLot();
            newLot.setItem(line.getItem());
            newLot.setWarehouse(shopWarehouse);
            newLot.setQtyInitial(qtyRemaining);
            newLot.setQtyRemaining(qtyRemaining);
            newLot.setUnitCost(unitCost);
            newLot.setReceivedDate(reportDate);
            StockLot savedLot = stockLotRepository.save(newLot);

            StockMovement in = new StockMovement();
            in.setLot(savedLot);
            in.setMovementType(MovementType.IN);
            in.setQty(qtyRemaining);
            in.setRefType("DAILY_REPORT_FINALIZE");
            in.setRefId(report.getId());
            in.setNote("Tồn kho SHOP cuối ngày " + reportDate);
            stockMovementRepository.save(in);

            log.debug("Cập nhật SHOP stock: item={}, qty={}", itemId, qtyRemaining);
        }
        log.info("Đã cập nhật tồn kho SHOP cho {} items sau finalize ngày {}", lines.size(), reportDate);
    }

    private void assertNotFinalized(DailyReport report) {
        if (report.getStatus() == DailyReportStatus.FINALIZED) {
            throw new IllegalStateException(
                    "Báo cáo ngày " + report.getReportDate() + " đã FINALIZED, không thể chỉnh sửa.");
        }
    }
}
