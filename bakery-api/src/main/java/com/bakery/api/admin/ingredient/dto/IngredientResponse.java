package com.bakery.api.admin.ingredient.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.common.entity.enums.BaseUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IngredientResponse extends BakeryBaseResponse {

    private String code;
    private String name;
    private BaseUnit baseUnit;
    private Boolean isActive;
}
