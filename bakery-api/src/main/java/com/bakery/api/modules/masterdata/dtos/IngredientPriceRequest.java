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
public class IngredientPriceRequest {

    @NotNull
    private UUID ingredientId;

    /** VND/kg (hoặc VND/L nếu base_unit = ML) */
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal pricePerKg;

    @NotNull
    private LocalDate effectiveDate;

    private String note;
}
