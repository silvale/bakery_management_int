package com.bakery.api.modules.sales.controllers;

import com.bakery.api.modules.sales.dtos.*;
import com.bakery.api.modules.sales.services.DailyShopReportService;
import com.bakery.api.modules.sales.services.PosSalesDataService;
import com.bakery.api.modules.sales.services.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3-way Reconciliation API — một điểm vào duy nhất cho toàn bộ đối chiếu.
 *
 * NGUỒN DỮ LIỆU (phải nạp trước khi gọi GET đối chiếu):
 *   POST /api/v1/reconciliation/pos-import   — anh Chính upload file POS
 *   POST /api/v1/reconciliation/shop-report  — nhân viên Shop submit hủy cuối ngày
 *   [TỰ ĐỘNG] TRANSFER ACTIVE               — bếp giao hàng qua phiếu TRANSFER
 *
 * ĐỐI CHIẾU (chạy sau khi đã nạp đủ 3 nguồn):
 *   GET /api/v1/reconciliation?date=...         — kết quả 1 ngày
 *   GET /api/v1/reconciliation/range?from=&to=  — kết quả khoảng ngày
 *   GET /api/v1/reconciliation/discrepancies    — chỉ các dòng có chênh lệch
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "3-way đối chiếu: Bếp giao → POS bán → Shop báo cáo hủy")
public class ReconciliationController {

    private final ReconciliationService   reconciliationService;
    private final PosSalesDataService     posSalesDataService;
    private final DailyShopReportService  shopReportService;

    /**
     * Reconciliation 1 ngày.
     *
     * GET /api/v1/reconciliation?date=2026-07-01
     * GET /api/v1/reconciliation?date=2026-07-01&branchId=xxx
     */
    @GetMapping
    @Operation(summary = "Kết quả reconciliation theo ngày (tuỳ chọn filter branch)")
    public ResponseEntity<ReconciliationSummaryResponse> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(reconciliationService.getByDate(date, branchId));
    }

    /**
     * Reconciliation theo khoảng ngày.
     *
     * GET /api/v1/reconciliation/range?from=2026-07-01&to=2026-07-07
     * GET /api/v1/reconciliation/range?from=...&to=...&branchId=xxx
     */
    @GetMapping("/range")
    @Operation(summary = "Reconciliation khoảng ngày (tuỳ chọn filter branch)")
    public ResponseEntity<ReconciliationSummaryResponse> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(reconciliationService.getByDateRange(from, to, branchId));
    }

    /**
     * Chỉ các dòng có chênh lệch (variance != 0) — dùng cho alert / daily summary.
     *
     * GET /api/v1/reconciliation/discrepancies?date=2026-07-01
     */
    @GetMapping("/discrepancies")
    @Operation(summary = "Danh sách sản phẩm có chênh lệch trong ngày")
    public ResponseEntity<List<ReconciliationRowResponse>> getDiscrepancies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(reconciliationService.getDiscrepanciesByDate(date));
    }

    // ── NGUỒN 2: Nạp dữ liệu POS ─────────────────────────────────────────────

    /**
     * Upload dữ liệu bán hàng từ máy POS (anh Chính thực hiện hàng ngày).
     * Idempotent — upload lại file cũng an toàn (upsert theo sales_date + branch + item).
     *
     * POST /api/v1/reconciliation/pos-import        — single row
     * POST /api/v1/reconciliation/pos-import/batch  — upload cả file POS (nhiều rows)
     */
    @PostMapping("/pos-import")
    @Operation(summary = "[Nguồn 2] Upload 1 dòng doanh số POS vào hệ thống")
    public ResponseEntity<PosSalesDataResponse> importPosRow(
            @Valid @RequestBody PosSalesDataRequest request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        return ResponseEntity.ok(posSalesDataService.upsert(request, actorName));
    }

    @PostMapping("/pos-import/batch")
    @Operation(summary = "[Nguồn 2] Batch upload file POS — upsert toàn bộ dữ liệu ngày")
    public ResponseEntity<Map<String, Object>> importPosBatch(
            @Valid @RequestBody List<PosSalesDataRequest> requests,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        List<PosSalesDataResponse> results = posSalesDataService.upsertBatch(requests, actorName);
        return ResponseEntity.ok(Map.of(
                "processed", results.size(),
                "message",   "Upload POS thành công — chạy GET /reconciliation?date=... để xem kết quả đối chiếu",
                "rows",      results
        ));
    }

    @GetMapping("/pos-import")
    @Operation(summary = "[Nguồn 2] Xem dữ liệu POS đã upload theo ngày")
    public ResponseEntity<List<PosSalesDataResponse>> getPosData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(posSalesDataService.findByDateAndBranch(date, branchId));
    }

    // ── NGUỒN 3: Nạp báo cáo hủy cuối ngày của Shop ─────────────────────────

    /**
     * Nhân viên Shop submit số lượng bánh hủy thực tế cuối ngày.
     * Idempotent — submit lại cũng an toàn (upsert theo report_date + branch + item).
     *
     * POST /api/v1/reconciliation/shop-report
     */
    @PostMapping("/shop-report")
    @Operation(summary = "[Nguồn 3] Nhân viên Shop submit báo cáo hủy cuối ngày")
    public ResponseEntity<DailyShopReportResponse> submitShopReport(
            @Valid @RequestBody DailyShopReportRequest request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        return ResponseEntity.ok(shopReportService.submit(request, actorName));
    }

    @GetMapping("/shop-report")
    @Operation(summary = "[Nguồn 3] Xem báo cáo hủy đã submit theo ngày")
    public ResponseEntity<List<DailyShopReportResponse>> getShopReports(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(shopReportService.findByDateAndBranch(date, branchId));
    }
}
