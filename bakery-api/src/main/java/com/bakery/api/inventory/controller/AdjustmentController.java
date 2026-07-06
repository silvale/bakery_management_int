package com.bakery.api.inventory.controller;

import com.bakery.api.framework.controller.TransactionBaseResource;
import com.bakery.api.inventory.dto.AdjustmentRequest;
import com.bakery.api.inventory.dto.AdjustmentResponse;
import com.bakery.api.inventory.service.AdjustmentCommandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý phiếu ĐIỀU CHỈNH (transaction_type = ADJUSTMENT).
 *
 * Hỗ trợ: LOSS | STOCKTAKE | SUPPLIER_RETURN | WRITE_OFF
 *   qty dương = tăng tồn   → tạo Inventory lot mới
 *   qty âm    = giảm tồn   → FEFO deduct
 *
 * Endpoints kế thừa từ TransactionBaseResource:
 *   GET  /api/v1/adjustments/active     — danh sách đã duyệt
 *   GET  /api/v1/adjustments/pending    — chờ duyệt
 *   GET  /api/v1/adjustments/rejected   — từ chối / đã hủy
 *   GET  /api/v1/adjustments/{id}       — chi tiết
 *   POST /api/v1/adjustments            — tạo phiếu
 *   PUT  /api/v1/adjustments/{id}       — cập nhật (PENDING only)
 *   DELETE /api/v1/adjustments/{id}     — hủy (PENDING only)
 *   POST /api/v1/adjustments/{id}/approve — duyệt (1 bước: PENDING→ACTIVE)
 *   POST /api/v1/adjustments/{id}/reject  — từ chối
 */
@RestController
@RequestMapping("/api/v1/adjustments")
@Tag(name = "Adjustments", description = "Quản lý phiếu điều chỉnh tồn kho (kiểm kê, hao hụt, trả NCC, xóa tồn)")
public class AdjustmentController extends TransactionBaseResource<AdjustmentRequest, AdjustmentResponse> {

    private final AdjustmentCommandService adjustmentCommandService;

    public AdjustmentController(AdjustmentCommandService adjustmentCommandService) {
        this.adjustmentCommandService = adjustmentCommandService;
    }

    @Override
    protected AdjustmentCommandService abstractCommand() {
        return adjustmentCommandService;
    }
}
