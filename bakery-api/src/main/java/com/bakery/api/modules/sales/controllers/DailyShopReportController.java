package com.bakery.api.modules.sales.controllers;

import com.bakery.api.modules.sales.dtos.DailyShopReportRequest;
import com.bakery.api.modules.sales.dtos.DailyShopReportResponse;
import com.bakery.api.modules.sales.services.DailyShopReportService;
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
import java.util.UUID;

/**
 * API cho nhân viên Shop submit báo cáo cuối ngày.
 *
 * Workflow:
 *   1. Shop nhập: qty tồn lý thuyết + qty hủy thực tế
 *   2. POST /api/v1/shop-reports (upsert — có thể submit lại)
 *   3. Data này là nguồn thứ 3 trong 3-way reconciliation
 */
@RestController
@RequestMapping("/api/v1/shop-reports")
@RequiredArgsConstructor
@Tag(name = "Shop Reports", description = "Báo cáo cuối ngày của nhân viên shop (nguồn 3/3 trong reconciliation)")
public class DailyShopReportController {

    private final DailyShopReportService reportService;

    /**
     * Submit / upsert báo cáo cuối ngày.
     * POST /api/v1/shop-reports
     */
    @PostMapping
    @Operation(summary = "Submit báo cáo hủy cuối ngày (upsert)")
    public ResponseEntity<DailyShopReportResponse> submit(
            @Valid @RequestBody DailyShopReportRequest request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        return ResponseEntity.ok(reportService.submit(request, actorName));
    }

    /**
     * Danh sách báo cáo theo ngày.
     * GET /api/v1/shop-reports?date=2026-07-01
     * GET /api/v1/shop-reports?date=2026-07-01&branchId=xxx
     */
    @GetMapping
    @Operation(summary = "Xem báo cáo cuối ngày theo ngày + branch")
    public ResponseEntity<List<DailyShopReportResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(reportService.findByDateAndBranch(date, branchId));
    }
}
