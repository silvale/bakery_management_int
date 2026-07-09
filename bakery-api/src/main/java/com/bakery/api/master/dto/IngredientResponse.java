package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IngredientResponse extends BaseResponse {

    private String code;
    private String name;
    private String unit;
    private String ingredientType;
    private ReferenceValue defaultSupplier;
    private BigDecimal lastPrice;
    private LocalDate lastPriceDate;
}
