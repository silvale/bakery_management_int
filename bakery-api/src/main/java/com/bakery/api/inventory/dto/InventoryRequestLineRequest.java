package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryRequestLineRequest(
        UUID itemId,
        BigDecimal quantity,
        String unit,
        /** Giá mua thực tế — bắt buộc với PURCHASE */
        BigDecimal unitCost,
        Integer sortOrder,
        String note) {}
