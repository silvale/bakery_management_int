package com.bakery.api.production.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ProductionPlanAdjustRequest;
import com.bakery.api.production.dto.ProductionPlanResponse;
import com.bakery.api.production.service.ProductionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kế hoạch sản xuất.
 *
 * <p>Manager: xem DRAFT + APPROVED, điều chỉnh, approve/reject.
 * <p>Bếp: chỉ xem danh sách APPROVED.
 */
@RestController
@RequestMapping("/api/v1/production/plans")
@RequiredArgsConstructor
public class ProductionPlanController {

    private final ProductionPlanService service;

    /** Bếp lấy danh sách kế hoạch đã APPROVED. */
    @GetMapping
    public List<ProductionPlanResponse> findApproved() {
        return service.findApproved();
    }

    /** Manager xem kế hoạch theo ngày (DRAFT hoặc APPROVED). */
    @GetMapping("/by-date")
    public ProductionPlanResponse findByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDate(date);
    }

    @GetMapping("/{id}")
    public ProductionPlanResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    /** Manager điều chỉnh số lượng khi còn DRAFT. */
    @PutMapping("/{id}/adjust")
    public ProductionPlanResponse adjust(
            @PathVariable UUID id,
            @Valid @RequestBody ProductionPlanAdjustRequest req) {
        return service.adjust(id, req);
    }

    /** Manager approve → bếp thấy ngay. */
    @PostMapping("/{id}/approve")
    public ProductionPlanResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    /** Manager reject DRAFT. */
    @PostMapping("/{id}/reject")
    public ProductionPlanResponse reject(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return service.reject(id, reason);
    }
}
