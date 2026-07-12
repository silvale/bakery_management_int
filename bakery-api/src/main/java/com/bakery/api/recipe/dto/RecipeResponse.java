/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.dto;

import java.util.List;
import java.util.UUID;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecipeResponse extends BaseResponse {

    /** Tham chiếu sản phẩm (code, name) — null nếu đây là recipe của SemiProduct */
    private ReferenceValue product;

    /** Tham chiếu bán thành phẩm (code, name) — null nếu đây là recipe của Product */
    private ReferenceValue semiProduct;

    private Integer version;

    /** true = đang được dùng trong sản xuất */
    private boolean active;

    private String note;

    /** ID recipe gốc nếu đây là bản clone */
    private UUID parentRecipeId;

    private List<RecipeLineResponse> lines;
}
