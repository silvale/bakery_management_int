package com.bakery.api.master.dto;

import java.util.UUID;

import com.bakery.api.master.entity.WarehouseKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 22: cờ null khi sửa = giữ nguyên, khi tạo = false. {@code kind} null = INTERNAL khi tạo, giữ nguyên khi sửa.
 * 23: {@code storeId} null = không thuộc cửa hàng (khác cờ: sửa mà không gửi thì bỏ gán). JSON {@code isProductionSource} / {@code isDeliveryHold} / {@code isSalesPoint} (tên theo spec).
 */
public record WarehouseRequest(
        @NotBlank String code,
        @NotBlank String name,
        String address,
        WarehouseKind kind,
        Boolean canReceivePurchase,
        Boolean isProductionSource,
        Boolean canStoreSemi,
        Boolean isDeliveryHold,
        Boolean isSalesPoint,
        Boolean canExportExternal,
        Integer sortOrder,
        UUID storeId) {}
