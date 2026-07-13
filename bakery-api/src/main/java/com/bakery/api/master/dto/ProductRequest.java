package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.util.List;

import com.bakery.api.recipe.dto.RecipeLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ProductRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String unit,
        String productType,
        String productCategory,
        BigDecimal sellingPrice,
        // Công thức đi kèm (bundled) — null = chưa có công thức, non-null = tạo/cập nhật CT luôn
        String recipeNote,
        @Valid List<RecipeLineRequest> recipeLines) {}
