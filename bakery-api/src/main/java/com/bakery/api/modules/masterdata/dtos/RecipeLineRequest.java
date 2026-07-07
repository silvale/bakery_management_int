package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.RecipeLineType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một dòng trong công thức.
 *
 * XOR: phải có đúng 1 trong 2 — ingredientId HOẶC semiProductId.
 * Validation được thực hiện trong ProductSupportService.beforeCreate().
 */
@Getter
@Setter
public class RecipeLineRequest {

    /** NULL nếu dùng semi_product */
    private UUID ingredientId;

    /** NULL nếu dùng ingredient */
    private UUID semiProductId;

    @NotNull
    @DecimalMin("0.001")
    private BigDecimal quantityGram;

    @NotNull
    private RecipeLineType lineType;

    private String note;
}
