package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.production.dto.CompleteLineRequest;
import com.bakery.api.production.dto.ProductionRequestRequest;
import com.bakery.api.production.dto.ProductionRequestResponse;
import com.bakery.api.production.service.ProductionIngredientService;
import com.bakery.api.production.service.ProductionRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final ProductionIngredientService ingredientService;

    @Override
    protected String screenCode() { return "PROD_REQUESTS"; }

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
        checkPermission("APPROVE");
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
        checkPermission("APPROVE");
        return service.completeLines(id, items);
    }

    /**
     * Approve toàn bộ phiếu SX trong ngày — tiện khi có nhiều phiếu cùng ngày.
     * Chỉ approve các phiếu đang ở DRAFT hoặc PENDING_APPROVAL.
     *
     * @param date ngày sản xuất (mặc định hôm nay)
     * @return danh sách phiếu đã được approve
     */
    @PostMapping("/approve-all")
    public List<ProductionRequestResponse> approveAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        checkPermission("APPROVE");
        return service.approveAll(date != null ? date : LocalDate.now());
    }

    /**
     * Gợi ý số lượng BTP cần sản xuất dựa trên Phiếu SX DAILY đang chờ trong ngày.
     *
     * <p>Response:
     * - {@code neededByPlan}: tổng BTP được dùng trong các DAILY PR của ngày đó
     * - {@code kitchenStock}: tồn BTP hiện tại trong kho bếp
     * - {@code suggested}: max(0, neededByPlan − kitchenStock)
     *
     * @param semiItemId ID của BTP cần sản xuất
     * @param date       ngày sản xuất (mặc định hôm nay)
     */
    @GetMapping("/suggest-semi")
    public java.util.Map<String, Object> suggestSemi(
            @RequestParam UUID semiItemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ingredientService.suggestSemiQty(semiItemId, date != null ? date : LocalDate.now());
    }

    /**
     * Tạo phiếu xuất kho MAIN → KITCHEN cho nguyên liệu cần để sản xuất BTP.
     *
     * <p>Chỉ xuất phần NL còn thiếu trong KITCHEN. Nếu MAIN không đủ,
     * xuất tối đa tồn MAIN và ghi chú NL nào bị bỏ qua.
     *
     * @param id ID Phiếu SX BTP (productionType = SEMI)
     */
    @PostMapping("/{id}/transfer-ingredients")
    public java.util.Map<String, Object> transferIngredients(@PathVariable UUID id) {
        checkPermission("CREATE");
        InventoryRequest req = ingredientService.generateSemiTransferRequest(id);
        return java.util.Map.of(
                "transferCode", req.getCode(),
                "transferId",   req.getId(),
                "status",       req.getApprovalStatus(),
                "lineCount",    req.getLines() != null ? req.getLines().size() : 0
        );
    }
}
