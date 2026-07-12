package com.bakery.api.report.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API báo cáo cuối ngày.
 *
 * POST /api/v1/daily-reports/init?reportDate=2026-07-10
 *   → Tạo hoặc lấy DRAFT report cho ngày đó
 *
 * GET  /api/v1/daily-reports/{id}
 *   → Xem header report
 *
 * GET  /api/v1/daily-reports/{id}/lines
 *   → Xem chi tiết từng sản phẩm
 *
 * POST /api/v1/daily-reports/{id}/remaining
 *   → Nhân viên nhập số bánh còn lại (qty_remaining_actual) theo từng item
 *
 * POST /api/v1/daily-reports/{id}/finalize
 *   → [ADMIN] Chốt báo cáo: tổng hợp DeliveryRecord + POS + tính discrepancy
 */
@RestController
@RequestMapping("/api/v1/daily-reports")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService service;

    @PostMapping("/init")
    public DailyReport initReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return service.getOrCreateDraft(reportDate);
    }

    @GetMapping("/{id}")
    public DailyReport getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/lines")
    public List<DailyReportLine> getLines(@PathVariable UUID id) {
        return service.getLines(id);
    }

    /**
     * Nhân viên nhập số bánh còn lại cuối ngày — độc lập với POS, bất cứ lúc nào.
     *
     * @param itemId             ID sản phẩm
     * @param qtyRemainingActual số bánh còn lại thực tế tại cửa hàng
     */
    @PostMapping("/{id}/remaining")
    public DailyReportLine updateRemaining(
            @PathVariable UUID id,
            @RequestParam UUID itemId,
            @RequestParam BigDecimal qtyRemainingActual,
            @RequestParam(required = false) String note) {
        return service.updateRemainingQty(id, itemId, qtyRemainingActual, note);
    }

    /**
     * [ADMIN] Chốt báo cáo ngày.
     * Tổng hợp: DeliveryRecord + POS + snapshot giá → FINALIZED.
     */
    @PostMapping("/{id}/finalize")
    public DailyReport finalize(@PathVariable UUID id) {
        return service.finalize(id);
    }
}
