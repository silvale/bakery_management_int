package com.bakery.api.inventory.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.inventory.dto.InventoryRequestRequest;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.service.InventoryRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý phiếu nhập/điều chuyển/điều chỉnh kho.
 *
 * <p>Framework endpoints: GET /list, /all, /{id}, POST, PUT, DELETE, /approve, /reject
 *
 * <p>Custom:
 * GET /api/v1/inventory-requests/by-warehouse?warehouseCode=MAIN&approvalStatus=PENDING_APPROVAL
 *   → tất cả phiếu liên quan đến kho đó (source OR target), theo status
 */
@RestController
@RequestMapping("/api/v1/inventory-requests")
@RequiredArgsConstructor
public class InventoryRequestController
        extends BakeryAdminResource<InventoryRequestRequest, InventoryRequestResponse> {

    private final InventoryRequestService service;

    @Override
    protected String screenCode() { return "INVENTORY_REQUESTS"; }

    @Override
    protected BakeryAdminService<InventoryRequestRequest, InventoryRequestResponse> getService() {
        return service;
    }

    /**
     * Tab pending/approved theo kho.
     *
     * <p>Trả tất cả phiếu mà kho này là nguồn (sourceWarehouse) HOẶC đích (targetWarehouse),
     * lọc theo approvalStatus. Dùng cho 3 tab kho trên UI.
     *
     * @param warehouseCode   mã kho: MAIN / KITCHEN / SHOP
     * @param approvalStatus  trạng thái: PENDING_APPROVAL / APPROVED / REJECTED / DRAFT
     */
    @GetMapping("/by-warehouse")
    public List<InventoryRequestResponse> byWarehouse(
            @RequestParam String warehouseCode,
            @RequestParam(defaultValue = "PENDING_APPROVAL") String approvalStatus) {
        return service.findByWarehouse(warehouseCode, approvalStatus);
    }

    /**
     * Cập nhật đơn giá cho 1 dòng trong phiếu nhập.
     * Body: { "unitCost": 12500.00 }
     */
    @PatchMapping("/{requestId}/lines/{lineId}")
    public ResponseEntity<Void> updateLineCost(
            @PathVariable UUID requestId,
            @PathVariable UUID lineId,
            @RequestBody Map<String, BigDecimal> body) {
        service.updateLineCost(requestId, lineId, body.get("unitCost"));
        return ResponseEntity.noContent().build();
    }
}
