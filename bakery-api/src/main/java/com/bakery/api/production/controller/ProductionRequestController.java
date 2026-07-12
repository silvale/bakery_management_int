package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.CompleteLineRequest;
import com.bakery.api.production.dto.ProductionRequestRequest;
import com.bakery.api.production.dto.ProductionRequestResponse;
import com.bakery.api.production.service.ProductionRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
     * Bếp bấm "Completed" trên 1 line → tạo DeliveryRecord(READY) + StockLot bánh thành phẩm.
     *
     * <p>Nếu qtyProduced ≠ plannedQty, bắt buộc truyền adjustmentType:
     * <ul>
     *   <li>INGREDIENT_VARIANCE — bếp lấy nhiều/ít NL → cần duyệt → cộng/trừ NL kho bếp</li>
     *   <li>PRODUCTION_WASTAGE  — hao hụt sản xuất → chỉ ghi nhận, không động NL</li>
     * </ul>
     */
    @PostMapping("/{id}/lines/{lineId}/complete")
    public ProductionRequestResponse completeLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestParam BigDecimal qtyProduced,
            @RequestParam(required = false) AdjustmentType adjustmentType,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String note) {
        return service.completeLine(id, lineId, qtyProduced, adjustmentType, reason, note);
    }

    /**
     * Complete nhiều line cùng lúc — tiện khi bếp submit cả phiếu 1 lần.
     *
     * <p>Toàn bộ xử lý trong 1 transaction: nếu 1 line lỗi thì rollback tất cả.
     *
     * <p>Request body (JSON array):
     * <pre>
     * [
     *   { "lineId": "uuid", "qtyProduced": 50 },
     *   { "lineId": "uuid", "qtyProduced": 45, "adjustmentType": "PRODUCTION_WASTAGE", "reason": "Bánh bị cháy" }
     * ]
     * </pre>
     */
    @PostMapping("/{id}/lines/complete-batch")
    public ProductionRequestResponse completeLines(
            @PathVariable UUID id,
            @RequestBody List<CompleteLineRequest> items) {
        return service.completeLines(id, items);
    }
}
