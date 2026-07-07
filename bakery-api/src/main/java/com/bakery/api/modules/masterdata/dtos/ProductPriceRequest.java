package com.bakery.api.modules.masterdata.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ProductPriceRequest {

    @NotNull
    private UUID productId;

    /** Giá bán VND/unit (cái hoặc kg tuỳ product_type) */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    /** Ngày hiệu lực. Phải được approve trước ngày mở bán. */
    @NotNull
    private LocalDate effectiveDate;

    private String note;
}
