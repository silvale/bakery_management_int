package com.bakery.api.inventory.dto;

import com.bakery.common.entity.enums.ItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionLineRequest(

        @NotNull UUID itemId,
        @NotNull ItemType itemType,

        @NotNull
        @DecimalMin("0.0001")
        BigDecimal qtyRequested,

        /** qty_approved: null = chưa duyệt, điền lúc approve nếu khác qty_requested */
        BigDecimal qtyApproved,

        @NotNull String unit,

        BigDecimal unitPrice,

        String note
) {}
