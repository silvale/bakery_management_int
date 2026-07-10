package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.production.dto.DeliveryRecordResponse;
import com.bakery.api.production.dto.ProductionRequestRequest;
import com.bakery.api.production.dto.ProductionRequestResponse;
import com.bakery.api.production.service.ProductionRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý phiếu sản xuất bánh.
 *
 * Framework endpoints (từ BakeryAdminResource):
 *   GET    /api/v1/production-requests                → list (phân trang)
 *   GET    /api/v1/production-requests/all            → list (không phân trang)
 *   GET    /api/v1/production-requests/{id}           → detail
 *   POST   /api/v1/production-requests                → tạo mới
 *   PUT    /api/v1/production-requests/{id}           → cập nhật
 *   DELETE /api/v1/production-requests/{id}           → xóa
 *   POST   /api/v1/production-requests/{id}/approve   → duyệt (trừ NL kho bếp)
 *   POST   /api/v1/production-requests/{id}/reject    → từ chối
 *
 * Custom endpoints:
 *   POST   /api/v1/production-requests/{id}/lines/{lineId}/complete  → bếp báo xong 1 line
 *   POST   /api/v1/delivery-records/{deliveryId}/confirm             → shop xác nhận nhận
 */
@RestController
@RequestMapping("/api/v1/production-requests")
@RequiredArgsConstructor
public class ProductionRequestController
        extends BakeryAdminResource<ProductionRequestRequest, ProductionRequestResponse> {

    private final ProductionRequestService service;

    @Override
    protected BakeryAdminService<ProductionRequestRequest, ProductionRequestResponse> getService() {
        return service;
    }

    /**
     * Bếp bấm "Completed" trên 1 line → tạo DeliveryRecord(READY) + StockLot bánh.
     *
     * @param id          ProductionRequest ID
     * @param lineId      ProductionRequestLine ID
     * @param qtyProduced số lượng thực tế làm ra
     * @param note        ghi chú (optional)
     */
    @PostMapping("/{id}/lines/{lineId}/complete")
    public ProductionRequestResponse completeLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestParam BigDecimal qtyProduced,
            @RequestParam(required = false) String note) {
        return service.completeLine(id, lineId, qtyProduced, note);
    }
}
