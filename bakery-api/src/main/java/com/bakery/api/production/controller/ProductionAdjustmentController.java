package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.api.production.service.ProductionAdjustmentService;
import com.bakery.framework.entity.AdjustmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý điều chỉnh sản lượng.
 *
 * GET  /api/v1/production-adjustments/pending                     → bếp trưởng xem danh sách chờ duyệt
 * GET  /api/v1/production-adjustments/by-delivery/{drId}          → lịch sử adjust của 1 delivery record
 * POST /api/v1/production-adjustments/{id}/approve                → bếp trưởng duyệt
 * POST /api/v1/production-adjustments/{id}/reject                 → bếp trưởng từ chối
 * POST /api/v1/production-adjustments/admin/correct               → [ADMIN ONLY] tạo correction
 */
@RestController
@RequestMapping("/api/v1/production-adjustments")
@RequiredArgsConstructor
public class ProductionAdjustmentController {

    private final ProductionAdjustmentService service;

    @GetMapping("/pending")
    public List<ProductionAdjustment> getPending() {
        return service.findPending();
    }

    @GetMapping("/by-delivery/{deliveryRecordId}")
    public List<ProductionAdjustment> getByDeliveryRecord(@PathVariable UUID deliveryRecordId) {
        return service.findByDeliveryRecord(deliveryRecordId);
    }

    @PostMapping("/{id}/approve")
    public ProductionAdjustment approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    public ProductionAdjustment reject(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return service.reject(id, reason);
    }

    /**
     * [ADMIN ONLY] Tạo điều chỉnh thủ công sau khi bếp đã submit.
     * Endpoint này chỉ expose cho role ADMIN — bảo vệ ở tầng security config.
     */
    @PostMapping("/admin/correct")
    public ProductionAdjustment adminCorrect(
            @RequestParam UUID deliveryRecordId,
            @RequestParam AdjustmentType adjustmentType,
            @RequestParam BigDecimal adjustedQty,
            @RequestParam(required = false) String reason) {
        return service.createAdminCorrection(deliveryRecordId, adjustmentType, adjustedQty, reason);
    }
}
