package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.enums.ItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionLineResponse(
        UUID id,
        UUID itemId,
        ItemType itemType,
        String itemName,      // resolved khi build response
        BigDecimal qtyRequested,
        BigDecimal qtyApproved,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal, // qtyApproved * unitPrice
        UUID lotId,           // lô kho được gắn sau khi approve (FEFO)
        String note
) {}
