package com.bakery.api.production.controller;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.production.dto.DeliveryRecordResponse;
import com.bakery.api.production.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
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
public class DeliveryRecordController {

    private final ProductionRequestService service;

    /**
     * Shop bấm "Xác nhận nhận" → cập nhật qtyReceived, tính discrepancy.
     *
     * @param id          DeliveryRecord ID
     * @param qtyReceived số lượng shop thực nhận
     * @param note        ghi chú (optional)
     */
    @PostMapping("/{id}/confirm")
    public DeliveryRecordResponse confirm(
            @PathVariable UUID id,
            @RequestParam BigDecimal qtyReceived,
            @RequestParam(required = false) String note) {
        return service.confirmDelivery(id, qtyReceived, note);
    }
}
