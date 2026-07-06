package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.LotStatus;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO Cancel Service — Xử lý hủy bánh cuối ngày.
 *
 * Flow:
 *   1. Admin nhập tổng số hủy theo Master Product
 *   2. Hệ thống FIFO trừ lô CŨ NHẤT (gần hết HSD nhất) trước
 *   3. Nếu tổng lô không đủ → WARNING, ghi log để admin kiểm tra
 *
 * Cron: ~20h hằng ngày (export danh sách cần hủy)
 * Cost hủy: theo giá lô thực tế (Option B)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelFifoService {

    private final ProductionLotRepository  productionLotRepository;
    private final CancelLogRepository      cancelLogRepository;
    private final CancelLogDetailRepository cancelLogDetailRepository;
    private final BranchRepository         branchRepository;
    private final ProductRepository        productRepository;

    // ── Xử lý hủy FIFO ───────────────────────────────────────

    /**
     * Xử lý hủy bánh FIFO cho 1 master product.
     *
     * @param productCode  Master product code
     * @param qtyToCancel  Tổng số lượng cần hủy
     * @param cancelDate   Ngày hủy
     * @param branchId     Chi nhánh
     */
    @Transactional
    public CancelResult processCancellation(
            String productCode,
            BigDecimal qtyToCancel,
            LocalDate cancelDate,
            java.util.UUID branchId) {

        Product product = productRepository.findByCode(productCode)
            .orElseThrow(() -> new IllegalArgumentException("SP không tìm thấy: " + productCode));

        Branch branch = branchRepository.findById(branchId).orElseThrow();

        log.info("FIFO Cancel | {} | Số lượng: {}", productCode, qtyToCancel);

        // Lấy các lô còn hàng, sắp xếp theo HSD ASC (gần hết hạn trước)
        List<ProductionLot> availableLots = productionLotRepository
            .findActiveLotsByProductOrderByExpiry(product.getId(), branchId);

        BigDecimal remaining    = qtyToCancel;
        BigDecimal totalCost    = BigDecimal.ZERO;
        List<CancelLogDetail> details = new ArrayList<>();
        List<String> warnings   = new ArrayList<>();

        // FIFO: trừ lô cũ nhất trước
        for (ProductionLot lot : availableLots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal canCancel = lot.getQtyRemaining()
                .min(remaining);

            // Cập nhật lô
            lot.setQtyCancelled(lot.getQtyCancelled().add(canCancel));
            if (lot.getQtyRemaining().compareTo(BigDecimal.ZERO) <= 0) {
                lot.setStatus(LotStatus.CANCELLED);
            } else {
                lot.setStatus(LotStatus.PARTIAL);
            }
            productionLotRepository.save(lot);

            // Tính cost theo giá lô (Option B)
            BigDecimal lotCost = canCancel
                .multiply(lot.getCostPerUnit())
                .setScale(4, java.math.RoundingMode.HALF_UP);
            totalCost = totalCost.add(lotCost);

            // Tạo detail record
            details.add(CancelLogDetail.builder()
                .productionLot(lot)
                .qtyCancelled(canCancel)
                .costPerUnit(lot.getCostPerUnit())
                .lotExpiryDate(lot.getExpiryDate())
                .build());

            remaining = remaining.subtract(canCancel);

            log.debug("  Trừ lô {} | {} cái | HSD: {} | Cost: {}đ",
                lot.getLotNumber(), canCancel, lot.getExpiryDate(), lotCost);
        }

        // Kiểm tra còn thừa không
        BigDecimal actualCancelled = qtyToCancel.subtract(remaining);
        String status = "OK";

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            status = details.isEmpty() ? "PARTIAL" : "WARNING";
            String warning = String.format(
                "Báo hủy %s cái nhưng chỉ trừ được %s cái (thiếu %s). " +
                "Kiểm tra: NV có thể đã bán/hủy nhầm lô.",
                qtyToCancel, actualCancelled, remaining
            );
            warnings.add(warning);
            log.warn("⚠ {}", warning);
        }

        // Lưu CancelLog
        CancelLog cancelLog = CancelLog.builder()
            .branch(branch)
            .product(product)
            .cancelDate(cancelDate)
            .qtyReported(qtyToCancel)
            .qtyCancelled(actualCancelled)
            .cancelledCost(totalCost)
            .cancelStatus(status)
            .warningNote(warnings.isEmpty() ? null : String.join("\n", warnings))
            .build();

        cancelLogRepository.save(cancelLog);

        // Lưu details
        details.forEach(d -> {
            d.setCancelLog(cancelLog);
            cancelLogDetailRepository.save(d);
        });

        log.info("✓ Cancel xong | {} | Đã trừ: {} | Cost: {}đ | Status: {}",
            productCode, actualCancelled, totalCost, status);

        return new CancelResult(
            productCode, qtyToCancel, actualCancelled,
            remaining, totalCost, status, warnings
        );
    }

    // ── Result record ─────────────────────────────────────────

    public record CancelResult(
        String         productCode,
        BigDecimal     qtyReported,
        BigDecimal     qtyCancelled,
        BigDecimal     qtyShortfall,   // > 0 = không đủ lô
        BigDecimal     cancelledCost,
        String         status,         // OK | WARNING | PARTIAL
        List<String>   warnings
    ) {
        public boolean hasWarning() {
            return !"OK".equals(status);
        }
    }
}
