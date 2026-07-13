/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.recipe.dto.RecipeLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Unified request cho mọi loại item: INGREDIENT, SEMI_PRODUCT, PRODUCT.
 * Field nào không áp dụng cho loại đó sẽ bị bỏ qua trong service.
 */
public record ItemRequest(
        /** INGREDIENT | SEMI_PRODUCT | PRODUCT */
        @NotBlank String itemType,
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String unit,

        // ── Ingredient only ──────────────────────────────────
        /** Code value key: INGREDIENT_TYPE */
        String ingredientType,
        UUID defaultSupplierId,

        // ── Product only ─────────────────────────────────────
        /** Code value key: PRODUCT_TYPE */
        String productType,
        /** Code value key: PRODUCT_CATEGORY */
        String productCategory,
        BigDecimal sellingPrice,

        // ── Recipe (Product + SemiProduct) ───────────────────
        String recipeNote,
        @Valid List<RecipeLineRequest> recipeLines) {}
