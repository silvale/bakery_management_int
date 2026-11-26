package com.bakery.api.inventory.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.inventory.dto.InventoryRequestRequest;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.dto.MarkPaidRequest;
import com.bakery.api.inventory.dto.MarkPaidResponse;
import com.bakery.api.inventory.dto.ReceiveRequest;
import com.bakery.api.inventory.service.InventoryRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.security.BakeryUserPrincipal;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    private final com.bakery.api.auth.service.AccessScope scope;

    @Override
    protected String screenCode() { return "INVENTORY_REQUESTS"; }

    /**
     * 11/BE-3, BE-4: phiếu kho gồm hai screen (INVENTORY_REQUESTS và STOCK_ADJUSTMENT theo loại phiếu), nên quyền theo
     * loại + kho + người tạo được kiểm trong {@link InventoryRequestService}. Ở đây chỉ giữ HISTORY (screen không đăng ký
     * action này → chỉ Admin).
     */
    @Override
    protected void checkPermission(String action) {
        if ("HISTORY".equals(action)) scope.require(screenCode(), action);
    }

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

    /**
     * 11/BE-6: Admin sửa đơn giá từng dòng SAU khi nhận hàng, trước khi thanh toán.
     * Body: [ { "lineId": "...", "unitCost": 20000 } ]. Đã thanh toán → 422.
     */
    @PutMapping("/{id}/line-prices")
    public ResponseEntity<InventoryRequestResponse> updateLinePrices(
            @PathVariable UUID id, @RequestBody List<InventoryRequestService.LinePrice> body) {
        return ResponseEntity.ok(service.updateLinePrices(id, body));
    }

    /**
     * Xác nhận nhận hàng theo số thực nhận (phiếu PURCHASE đang chờ duyệt) rồi nhập kho.
     * Body: { "lines": [ { "lineId": "...", "receivedQty": 15 }, ... ] } — đủ mọi dòng.
     */
    @PostMapping("/{id}/receive")
    public ResponseEntity<InventoryRequestResponse> receive(
            @PathVariable UUID id, @RequestBody ReceiveRequest body) {
        return ResponseEntity.ok(service.receive(id, body));
    }

    /**
     * Xác nhận thanh toán nhiều phiếu nhập — tất cả hoặc không gì cả. Chỉ SUPER_ADMIN.
     * Body: { "ids": ["...", "..."] }
     */
    @PostMapping("/mark-paid")
    public ResponseEntity<MarkPaidResponse> markPaid(@RequestBody MarkPaidRequest body) {
        requireSuperAdmin();
        return ResponseEntity.ok(service.markPaid(body.ids()));
    }

    /** Hủy xác nhận thanh toán 1 phiếu. Chỉ SUPER_ADMIN. */
    @PostMapping("/{id}/mark-unpaid")
    public ResponseEntity<InventoryRequestResponse> markUnpaid(@PathVariable UUID id) {
        requireSuperAdmin();
        return ResponseEntity.ok(service.markUnpaid(id));
    }

    /**
     * D6: kiểm thẳng role, không qua screen_registry — action ở đó cấp được cho role khác.
     */
    private void requireSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof BakeryUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        if (!"SUPER_ADMIN".equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin được thực hiện thao tác này.");
        }
    }
}
