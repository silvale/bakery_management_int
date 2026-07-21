package com.bakery.api.report.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.bakery.framework.security.RequirePermission;

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
@RequirePermission(screen = "DAILY_REPORT", action = "VIEW")
public class DailyReportController {

    private final DailyReportService service;

    @PostMapping("/init")
    @RequirePermission(screen = "DAILY_REPORT", action = "CREATE")
    public Map<String, Object> initReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        DailyReport r = service.getOrCreateDraft(reportDate);
        return Map.of("id", r.getId(), "reportDate", r.getReportDate(), "status", r.getStatus());
    }

    @GetMapping("/{id}/lines")
    public List<Map<String, Object>> getLines(@PathVariable UUID id) {
        return service.getLines(id).stream().map(l -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", l.getId());
            if (l.getItem() != null) {
                m.put("item", Map.of(
                        "id", l.getItem().getId(),
                        "key", l.getItem().getCode() != null ? l.getItem().getCode() : "",
                        "name", l.getItem().getName() != null ? l.getItem().getName() : ""));
            }
            m.put("qtyProduced",         l.getQtyProduced());
            m.put("qtyReceived",         l.getQtyReceived());
            m.put("qtyRemainingActual",  l.getQtyRemainingActual());
            m.put("qtySoldImplied",      l.getQtySoldImplied());
            m.put("qtySoldPos",          l.getQtySoldPos());
            m.put("discrepancyKitchen",  l.getDiscrepancyKitchen());
            m.put("discrepancyPos",      l.getDiscrepancyPos());
            m.put("qtyCancelled",        l.getQtyCancelled());
            m.put("discrepancyCancel",   l.getDiscrepancyCancel());
            m.put("sellingPrice",        l.getSellingPrice());
            m.put("note",                l.getNote() != null ? l.getNote() : "");
            return m;
        }).toList();
    }

    /**
     * Nhân viên nhập số bánh còn lại cuối ngày — độc lập với POS, bất cứ lúc nào.
     *
     * @param itemId             ID sản phẩm
     * @param qtyRemainingActual số bánh còn lại thực tế tại cửa hàng
     */
    @PostMapping("/{id}/remaining")
    @RequirePermission(screen = "DAILY_REPORT", action = "CREATE")
    public ResponseEntity<Void> updateRemaining(
            @PathVariable UUID id,
            @RequestParam UUID itemId,
            @RequestParam BigDecimal qtyRemainingActual,
            @RequestParam(required = false) String note) {
        service.updateRemainingQty(id, itemId, qtyRemainingActual, note);
        return ResponseEntity.noContent().build();
    }

    /**
     * Danh sách bánh cần hủy hôm nay.
     * Lọc tất cả sản phẩm có shelf_days = 0 + trạng thái remaining/cancelled hiện tại.
     */
    @GetMapping("/{id}/cancel-list")
    public List<Map<String, Object>> getCancelList(@PathVariable UUID id) {
        return service.getCancelList(id);
    }

    /**
     * Nhân viên nhập số bánh đã hủy cuối ngày.
     *
     * @param itemId       ID sản phẩm
     * @param qtyCancelled số bánh đã hủy
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(screen = "DAILY_REPORT", action = "CREATE")
    public ResponseEntity<Void> updateCancelled(
            @PathVariable UUID id,
            @RequestParam UUID itemId,
            @RequestParam BigDecimal qtyCancelled) {
        service.updateCancelledQty(id, itemId, qtyCancelled);
        return ResponseEntity.noContent().build();
    }

    /**
     * [ADMIN] Chốt báo cáo ngày.
     * Tổng hợp: DeliveryRecord + POS + snapshot giá → FINALIZED.
     */
    @PostMapping("/{id}/finalize")
    @RequirePermission(screen = "DAILY_REPORT", action = "FINALIZE")
    public Map<String, Object> finalize(@PathVariable UUID id) {
        DailyReport r = service.finalize(id);
        return Map.of("id", r.getId(), "reportDate", r.getReportDate(),
                "status", r.getStatus(),
                "finalizedBy", r.getFinalizedBy() != null ? r.getFinalizedBy() : "");
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable UUID id) {
        DailyReport r = service.getById(id);
        return Map.of("id", r.getId(), "reportDate", r.getReportDate(),
                "status", r.getStatus(),
                "finalizedBy", r.getFinalizedBy() != null ? r.getFinalizedBy() : "");
    }

    /**
     * Tìm report theo ngày — trả về null body (204) nếu chưa có.
     * Khác /init: không tạo mới.
     */
    @GetMapping("/by-date")
    public ResponseEntity<Map<String, Object>> findByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return service.findByDate(reportDate)
                .map(r -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", r.getId(),
                        "reportDate", r.getReportDate(),
                        "status", r.getStatus(),
                        "finalizedBy", r.getFinalizedBy() != null ? r.getFinalizedBy() : "")))
                .orElse(ResponseEntity.noContent().build());
    }
}
