/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unified response cho /api/v1/items — superset của mọi loại Item.
 * Fields không áp dụng cho loại đó sẽ là null.
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemResponse extends BaseResponse {

    /** INGREDIENT | SEMI_PRODUCT | PRODUCT */
    private String itemType;

    // ── Common fields ─────────────────────────────────────────
    private String code;
    private String name;
    private String unit;

    /** Nhóm sản phẩm / phòng SX — thay thế productCategory */
    private ReferenceValue itemGroup;

    // ── Ingredient only ───────────────────────────────────────
    /** Code value key: INGREDIENT_TYPE */
    private String ingredientType;
    private ReferenceValue defaultSupplier;
    private BigDecimal lastPrice;
    private LocalDate lastPriceDate;

    // ── Splittable (common) ──────────────────────────────────────
    /** true = có thể xuất lẻ; false = phải xuất theo bội số unitSize */
    private boolean splittable = true;
    private BigDecimal unitSize;

    // ── Product only ──────────────────────────────────────────
    /** Code value key: PRODUCT_TYPE */
    private String productType;
    /** Hạn sử dụng (ngày kể từ ngày SX). 0 = trong ngày. null = chưa cấu hình. */
    private Integer shelfDays;

    // ── Cost ──────────────────────────────────────────────────
    /**
     * Giá vốn per unit.
     * INGREDIENT = nhập tay; SEMI_PRODUCT / PRODUCT = tính từ công thức khi approve.
     */
    private BigDecimal unitCost;

    // ── Recipe (Product + SemiProduct) ────────────────────────
    /** Công thức active; nếu chưa active thì là phiên bản mới nhất */
    private RecipeResponse recipe;
}
