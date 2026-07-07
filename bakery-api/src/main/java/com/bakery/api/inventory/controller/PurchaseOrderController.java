package com.bakery.api.inventory.controller;

import com.bakery.api.framework.controller.TransactionBaseResource;
import com.bakery.api.inventory.dto.ImportRequest;
import com.bakery.api.inventory.dto.ImportResponse;
import com.bakery.api.inventory.service.ImportCommandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý phiếu NHẬP KHO (transaction_type = IMPORT).
 *
 * Endpoints kế thừa từ TransactionBaseResource:
 *   GET  /api/v1/purchase-orders/active     — danh sách đã duyệt
 *   GET  /api/v1/purchase-orders/pending    — chờ duyệt
 *   GET  /api/v1/purchase-orders/rejected   — từ chối / đã hủy
 *   GET  /api/v1/purchase-orders/{id}       — chi tiết
 *   POST /api/v1/purchase-orders            — tạo phiếu
 *   PUT  /api/v1/purchase-orders/{id}       — cập nhật (PENDING only)
 *   DELETE /api/v1/purchase-orders/{id}     — hủy (PENDING only)
 *   POST /api/v1/purchase-orders/{id}/approve — duyệt (1 bước: PENDING→ACTIVE)
 *   POST /api/v1/purchase-orders/{id}/reject  — từ chối
 */
@RestController("inventoryPurchaseOrderController")
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Purchase Orders", description = "Quản lý phiếu nhập kho nguyên liệu / hàng hóa")
public class PurchaseOrderController extends TransactionBaseResource<ImportRequest, ImportResponse> {

    private final ImportCommandService importCommandService;

    public PurchaseOrderController(ImportCommandService importCommandService) {
        this.importCommandService = importCommandService;
    }

    @Override
    protected ImportCommandService abstractCommand() {
        return importCommandService;
    }
}
