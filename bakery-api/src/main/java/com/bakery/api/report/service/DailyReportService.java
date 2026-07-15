package com.bakery.api.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.pricing.entity.ProductPrice;
import com.bakery.api.pricing.repository.ProductPriceRepository;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.production.service.ProductionPlannerService;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.entity.PosDailySale;
import com.bakery.api.report.repository.DailyReportLineRepository;
import com.bakery.api.report.repository.DailyReportRepository;
import com.bakery.api.report.repository.PosDailySaleRepository;
import com.bakery.framework.entity.DailyReportStatus;
import com.bakery.framework.entity.DeliveryStatus;
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
    private final BakeryActorResolver actorResolver;
    private final ProductionPlannerService productionPlannerService;

    // ── Get / Create ─────────────────────────────────────────────

    @Transactional
    public DailyReport getOrCreateDraft(LocalDate date) {
        return reportRepository.findByReportDate(date).orElseGet(() -> {
            DailyReport report = new DailyReport();
            report.setReportDate(date);
            report.setStatus(DailyReportStatus.DRAFT);
            report.setCreatedBy(actorResolver.currentUserId());
            return reportRepository.save(report);
        });
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

    // ── Nhân viên nhập qty_remaining_actual ─────────────────────

    /**
     * Nhân viên nhập số bánh còn lại cuối ngày tại cửa hàng, theo từng item.
     * Có thể gọi bất kỳ lúc nào trước khi FINALIZED.
     * Tự upsert: tạo line nếu chưa có.
     */
    @Transactional
    public DailyReportLine updateRemainingQty(UUID reportId, UUID itemId,
            BigDecimal qtyRemainingActual, String note) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);

        DailyReportLine line = lineRepository
                .findByDailyReportIdAndItemId(reportId, itemId)
                .orElseGet(() -> {
                    DailyReportLine l = new DailyReportLine();
                    l.setDailyReport(report);
                    l.setItem(itemRepository.getReferenceById(itemId));
                    return l;
                });

        line.setQtyRemainingActual(qtyRemainingActual);
        line.setNote(note);
        line.setUpdatedAt(Instant.now());

        // Tính qty_sold_implied nếu đã có qty_received
        if (line.getQtyReceived() != null && qtyRemainingActual != null) {
            line.setQtySoldImplied(line.getQtyReceived().subtract(qtyRemainingActual));
        }

        return lineRepository.save(line);
    }

    // ── Admin FINALIZE ───────────────────────────────────────────

    /**
     * Admin chốt báo cáo ngày:
     * 1. Tổng hợp qtyProduced + qtyReceived từ DeliveryRecord
     * 2. Tổng hợp qtySoldPos từ pos_daily_sale (qua product_mapping)
     * 3. Tính discrepancy 2 chiều
     * 4. Snapshot giá bán tại thời điểm chốt
     * 5. Status → FINALIZED
     */
    @Transactional
    public DailyReport finalize(UUID reportId) {
        DailyReport report = getById(reportId);
        assertNotFinalized(report);

        LocalDate date = report.getReportDate();

        // ── Bước 1: Tổng hợp DeliveryRecord theo ngày ───────────
        List<DeliveryRecord> deliveries = deliveryRecordRepository
                .findByProductionRequestLine_ProductionRequest_ProductionDate(date);

        // group by item → sum qtyProduced, sum qtyReceived
        Map<UUID, BigDecimal[]> byItem = deliveries.stream()
                .filter(dr -> dr.getProductionRequestLine() != null
                        && dr.getProductionRequestLine().getProduct() != null)
                .collect(Collectors.groupingBy(
                        dr -> dr.getProductionRequestLine().getProduct().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            BigDecimal produced = list.stream()
                                    .map(DeliveryRecord::getQtyProduced)
                                    .filter(q -> q != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal received = list.stream()
                                    .map(DeliveryRecord::getQtyReceived)
                                    .filter(q -> q != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            return new BigDecimal[]{produced, received};
                        })
                ));

        // ── Bước 2: Tổng hợp POS theo ngày ──────────────────────
        List<PosDailySale> posSales = posSaleRepository.findBySaleDate(date);
        // Map EX_CODE → item_id (qua product_mapping)
        Map<UUID, BigDecimal> posByItem = posSales.stream()
                .map(ps -> {
                    Optional<ProductMapping> mapping = productMappingRepository.findByExCode(ps.getExCode());
                    return mapping.map(m -> Map.entry(m.getItem().getId(), ps.getQtySold())).orElse(null);
                })
                .filter(e -> e != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        BigDecimal::add
                ));

        // ── Bước 3: Upsert DailyReportLine cho từng item ─────────
        for (Map.Entry<UUID, BigDecimal[]> entry : byItem.entrySet()) {
            UUID itemId = entry.getKey();
            BigDecimal qtyProduced = entry.getValue()[0];
            BigDecimal qtyReceived = entry.getValue()[1];

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

            // POS qty
            BigDecimal qtySoldPos = posByItem.getOrDefault(itemId, BigDecimal.ZERO);
            line.setQtySoldPos(qtySoldPos);

            // qty_sold_implied (nếu nhân viên đã nhập qty_remaining)
            if (line.getQtyRemainingActual() != null) {
                line.setQtySoldImplied(qtyReceived.subtract(line.getQtyRemainingActual()));
            }

            // Discrepancies
            line.setDiscrepancyKitchen(qtyProduced.subtract(qtyReceived));
            if (line.getQtySoldImplied() != null) {
                line.setDiscrepancyPos(line.getQtySoldImplied().subtract(qtySoldPos));
            }

            // Snapshot giá bán mới nhất
            List<ProductPrice> prices = productPriceRepository.findByItemIdOrderByEffectiveDateDesc(itemId);
            if (!prices.isEmpty()) {
                line.setSellingPrice(prices.get(0).getPrice());
            }

            line.setUpdatedAt(Instant.now());
            lineRepository.save(line);
        }

        // ── Bước 4: FINALIZE ─────────────────────────────────────
        report.setStatus(DailyReportStatus.FINALIZED);
        report.setFinalizedAt(Instant.now());
        report.setFinalizedBy(actorResolver.currentUserId());
        report.setUpdatedAt(Instant.now());
        DailyReport saved = reportRepository.save(report);

        // ── Bước 5: Tự động tạo kế hoạch SX ngày mai ────────────
        try {
            productionPlannerService.generateDraft(saved);
            log.info("Đã tạo kế hoạch SX cho ngày {}", saved.getReportDate().plusDays(1));
        } catch (Exception ex) {
            // Không để lỗi planner rollback finalize — log và tiếp tục
            log.error("Lỗi tạo kế hoạch SX sau finalize báo cáo {}: {}", saved.getId(), ex.getMessage(), ex);
        }

        return saved;
    }

    // ── Private ──────────────────────────────────────────────────

    private void assertNotFinalized(DailyReport report) {
        if (report.getStatus() == DailyReportStatus.FINALIZED) {
            throw new IllegalStateException(
                    "Báo cáo ngày " + report.getReportDate() + " đã FINALIZED, không thể chỉnh sửa.");
        }
    }
}
