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

        /** Nhóm sản phẩm / phòng SX — thay thế productCategory */
        UUID itemGroupId,

        // ── Ingredient only ──────────────────────────────────
        /** Code value key: INGREDIENT_TYPE */
        String ingredientType,
        UUID defaultSupplierId,

        // ── Splittable (Ingredient + Product) ────────────────
        /** true (default) = có thể xuất lẻ. false = phải xuất theo bội số unitSize. */
        boolean splittable,
        /** Kích thước đơn vị tối thiểu không thể tách. Ví dụ: bơ 5kg/cục → 5.0 */
        BigDecimal unitSize,

        // ── Product only ─────────────────────────────────────
        /** Code value key: PRODUCT_TYPE */
        String productType,
        /** Hạn sử dụng (ngày). 0 = bánh tươi trong ngày. null = không set. */
        Integer shelfDays,

        // ── Cost ─────────────────────────────────────────────
        /**
         * Giá vốn per unit — chỉ nhập trực tiếp cho INGREDIENT.
         * SEMI_PRODUCT / PRODUCT: tính tự động từ công thức, field này bị bỏ qua.
         */
        BigDecimal unitCost,

        // ── Recipe (Product + SemiProduct) ───────────────────
        String recipeNote,
        @Valid List<RecipeLineRequest> recipeLines) {}
