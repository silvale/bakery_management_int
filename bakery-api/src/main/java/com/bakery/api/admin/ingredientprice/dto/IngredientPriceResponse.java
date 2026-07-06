package com.bakery.api.admin.ingredientprice.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class IngredientPriceResponse extends BakeryBaseResponse {

    private UUID ingredientId;
    private String ingredientCode;
    private String ingredientName;
    private BigDecimal pricePerKg;
    private Integer version;
    private LocalDate effectiveDate;
    private String note;
}
