package com.bakery.api.inventory.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.framework.entity.InventoryRequestType;

public record InventoryRequestRequest(
        InventoryRequestType requestType,
        LocalDate requestDate,
        LocalDate expectedDeliveryDate,
        /** Kho xuất — bắt buộc khi TRANSFER */
        UUID sourceWarehouseId,
        /** Kho nhận hàng — bắt buộc khi PURCHASE/TRANSFER, hoặc kho điều chỉnh khi ADJUSTMENT */
        UUID targetWarehouseId,
        /** NCC — bắt buộc khi PURCHASE */
        UUID supplierId,
        String note,
        List<InventoryRequestLineRequest> lines) {}
