/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    private ReferenceValue defaultSupplier;
    private BigDecimal lastPrice;
    private LocalDate lastPriceDate;

    // ── Splittable ───────────────────────────────────────────────
    /** true = có thể xuất lẻ; false = phải xuất nguyên pack */
    private boolean splittable = true;

    /** Danh sách đóng gói (chỉ có giá trị với INGREDIENT) */
    private List<PackagingResponse> packagings;

    // ── Product only ──────────────────────────────────────────
    /** Hạn sử dụng (ngày kể từ ngày SX). 0 = trong ngày. null = chưa cấu hình. */
    private Integer shelfDays;

    // ── Cost ──────────────────────────────────────────────────
    /**
     * Giá vốn per unit.
     * INGREDIENT = nhập tay; SEMI_PRODUCT / PRODUCT = tính từ công thức khi approve.
     */
    private BigDecimal unitCost;

    // ── Stock threshold
    private java.math.BigDecimal minStockQuantity;

    // ── Image ────────────────────────────────────────────────
    /** Đường dẫn ảnh đại diện (optional). null = chưa có ảnh. */
    private String imageUrl;

    // ── Recipe (Product + SemiProduct) ────────────────────────
    /** Công thức active; nếu chưa active thì là phiên bản mới nhất */
    private RecipeResponse recipe;

    /** DTO cho 1 packaging option của item. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PackagingResponse {
        private java.util.UUID id;
        private String code;
        private String name;
        private BigDecimal qtyPerPack;
        private boolean isDefault;
    }
}
