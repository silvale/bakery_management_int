package com.bakery.api.report.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.report.entity.CancelRecord;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.repository.DailyReportRepository;
import com.bakery.api.report.service.CancelRecordService;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API quản lý danh sách hủy bánh theo EX_CODE.
 *
 * <p>GET  /api/v1/cancel-records?date=YYYY-MM-DD  — lấy danh sách theo ngày
 * <p>PUT  /api/v1/cancel-records/{id}/confirm     — NV xác nhận hủy
 * <p>POST /api/v1/cancel-records/extra            — NV thêm hủy vượt
 * <p>DELETE /api/v1/cancel-records/{id}           — xóa hủy vượt
 */
@RestController
@RequestMapping("/api/v1/cancel-records")
@RequiredArgsConstructor
@RequirePermission(screen = "HUY_BANH", action = "VIEW")
@com.bakery.api.auth.scope.RequireWarehouse(role = {com.bakery.api.master.entity.WarehouseRole.SALES_POINT})
public class CancelRecordController {

    private final CancelRecordService cancelRecordService;
    private final DailyReportRepository reportRepository;

    /** Lấy danh sách hủy theo ngày (mặc định hôm nay). Chưa có báo cáo ngày → trả danh sách rỗng. */
    @GetMapping
    public List<Map<String, Object>> getCancelList(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        // Chưa có báo cáo ngày = chưa có gì để hủy → trả rỗng thay vì 404
        return reportRepository.findByReportDate(target)
                .map(report -> cancelRecordService.getCancelList(report.getId(), target))
                .orElseGet(List::of);
    }

    /** NV tick xác nhận đã hủy đúng hoặc nhập số lượng thực tế khác. */
    @PutMapping("/{id}/confirm")
    @RequirePermission(screen = "HUY_BANH", action = "CREATE")
    public CancelRecord confirm(
            @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal qtyCancelActual,
            @RequestParam(required = false) String note) {
        return cancelRecordService.confirm(id, qtyCancelActual, note);
    }

    /** NV thêm hủy vượt (DAMAGED / OTHER) */
    @PostMapping("/extra")
    @RequirePermission(screen = "HUY_BANH", action = "CREATE")
    public CancelRecord addExtra(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String exCode,
            @RequestParam BigDecimal qtyCancelActual,
            @RequestParam(defaultValue = "DAMAGED") String cancelType,
            @RequestParam(required = false) String note) {
        DailyReport report = reportRepository.findByReportDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("DailyReport for date " + date));
        return cancelRecordService.addExtra(report, exCode, qtyCancelActual, cancelType, note);
    }

    /** Xóa hủy vượt (chỉ DAMAGED/OTHER, không xóa EXPIRED) */
    @DeleteMapping("/{id}")
    @RequirePermission(screen = "HUY_BANH", action = "CREATE")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        cancelRecordService.remove(id);
        return ResponseEntity.noContent().build();
    }
}
