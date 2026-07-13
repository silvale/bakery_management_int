package com.bakery.api.master.dto;

import java.math.BigDecimal;

import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductResponse extends BaseResponse {

    private String code;
    private String name;
    private String unit;
    private String productType;
    private String productCategory;
    private BigDecimal sellingPrice;

    /** Công thức đang active; nếu chưa active thì là phiên bản mới nhất (PENDING/DRAFT). */
    private RecipeResponse recipe;
}
