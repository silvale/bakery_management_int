package com.bakery.api.modules.sales.controllers;

import com.bakery.api.modules.sales.dtos.PosSalesDataRequest;
import com.bakery.api.modules.sales.dtos.PosSalesDataResponse;
import com.bakery.api.modules.sales.services.PosSalesDataService;
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
 * API upload dữ liệu POS (nguồn 2/3 trong 3-way reconciliation).
 *
 * Workflow:
 *   Hàng ngày Chính upload file POS → FE đọc file → gọi POST /api/v1/pos-sales/batch
 *   Các dòng đã tồn tại sẽ bị ghi đè (upsert — an toàn khi re-upload).
 */
@RestController
@RequestMapping("/api/v1/pos-sales")
@RequiredArgsConstructor
@Tag(name = "POS Sales", description = "Dữ liệu doanh số từ máy POS (nguồn 2/3 trong reconciliation)")
public class PosSalesDataController {

    private final PosSalesDataService posService;

    /**
     * Upsert 1 dòng POS.
     * POST /api/v1/pos-sales
     */
    @PostMapping
    @Operation(summary = "Upload / cập nhật 1 dòng POS (upsert)")
    public ResponseEntity<PosSalesDataResponse> upsert(
            @Valid @RequestBody PosSalesDataRequest request,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        return ResponseEntity.ok(posService.upsert(request, actorName));
    }

    /**
     * Batch upsert — upload cả file POS.
     * POST /api/v1/pos-sales/batch
     */
    @PostMapping("/batch")
    @Operation(summary = "Batch upload file POS (upsert từng dòng)")
    public ResponseEntity<Map<String, Object>> upsertBatch(
            @Valid @RequestBody List<PosSalesDataRequest> requests,
            @AuthenticationPrincipal UserDetails actor) {

        String actorName = actor != null ? actor.getUsername() : "system";
        List<PosSalesDataResponse> results = posService.upsertBatch(requests, actorName);
        return ResponseEntity.ok(Map.of(
                "processed", results.size(),
                "rows", results
        ));
    }

    /**
     * Xem dữ liệu POS theo ngày.
     * GET /api/v1/pos-sales?date=2026-07-01
     * GET /api/v1/pos-sales?date=2026-07-01&branchId=xxx
     */
    @GetMapping
    @Operation(summary = "Xem dữ liệu POS theo ngày + branch")
    public ResponseEntity<List<PosSalesDataResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(posService.findByDateAndBranch(date, branchId));
    }
}
