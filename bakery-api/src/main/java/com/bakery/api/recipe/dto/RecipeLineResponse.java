/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeLineResponse {
    private UUID id;
    /** item_type = INGREDIENT hoặc SEMI_PRODUCT */
    private ReferenceValue item;
    private String itemType;
    private BigDecimal quantity;
    private String unit;
    private Integer sortOrder;
}
