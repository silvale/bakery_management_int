package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;

import com.bakery.api.production.dto.DeliveryRecordResponse;
import com.bakery.api.production.service.ProductionRequestService;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shop xác nhận nhận bánh từ bếp.
 *
 *   POST /api/v1/delivery-records/{id}/confirm  → shop nhập qtyReceived
 */
@RestController
@RequestMapping("/api/v1/delivery-records")
@RequiredArgsConstructor
@RequirePermission(screen = "DELIVERY_RECORDS", action = "VIEW")
@com.bakery.api.auth.scope.RequireWarehouse(role = {com.bakery.api.master.entity.WarehouseRole.DELIVERY_HOLD, com.bakery.api.master.entity.WarehouseRole.SALES_POINT})
public class DeliveryRecordController {

    private final ProductionRequestService service;

    /** Lấy danh sách giao nhận theo ngày (mặc định hôm nay). */
    @GetMapping
    public List<DeliveryRecordResponse> findByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return service.findDeliveryRecordsByDate(target);
    }

    /**
     * Shop bấm "Xác nhận nhận" → cập nhật qtyReceived, tính discrepancy.
     *
     * <p>Body JSON: {@code {"qtyReceived":3,"note":"...","designs":[{"designId":"..","qty":1}]}}.
     * Bánh kem có mẫu: {@code designs} bắt buộc, tổng = qtyReceived; response có {@code stockWarnings}.
     *
     * @param id DeliveryRecord ID
     */
    @com.bakery.api.auth.scope.RequireWarehouse(role = {com.bakery.api.master.entity.WarehouseRole.SALES_POINT})
    @PostMapping("/{id}/confirm")
    @RequirePermission(screen = "DELIVERY_RECORDS", action = "APPROVE")
    public DeliveryRecordResponse confirm(
            @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestBody(required = false)
            com.bakery.api.production.dto.ConfirmDeliveryRequest request) {
        if (request == null || request.qtyReceived() == null) {
            throw new IllegalArgumentException(
                    "Phải gửi số lượng đã nhận");
        }
        return service.confirmDelivery(id, request.qtyReceived(), request.note(), request.designs());
    }
}
