package com.bakery.api.modules.sales.dtos;

import com.bakery.api.framework.enums.ItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request upload 1 dòng dữ liệu POS.
 * Thường được gọi theo batch (list) từ file POS.
 * Nếu đã tồn tại (sales_date + branch_id + item_id) thì upsert.
 */
public record PosSalesDataRequest(

        @NotNull LocalDate salesDate,

        @NotNull UUID branchId,

        /** product.id sau khi map từ SKU POS qua product_mapping */
        @NotNull UUID itemId,

        ItemType itemType,

        @NotNull
        @DecimalMin("0")
        BigDecimal qtySoldPos,

        @DecimalMin("0")
        BigDecimal revenue
) {}
