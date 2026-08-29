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
        UUID defaultSupplierId,

        // ── Splittable ────────────────────────────────────────
        /** true (default) = có thể xuất lẻ. false = phải xuất nguyên pack. */
        boolean splittable,

        // ── Product only ─────────────────────────────────────
        /** Hạn sử dụng (ngày). 0 = bánh tươi trong ngày. null = không set. */
        Integer shelfDays,

        // ── Stock threshold ─────────────────────────────────
        /**
         * Ngưỡng tồn kho tối thiểu — chỉ áp dụng cho INGREDIENT và SEMI_PRODUCT.
         * Cảnh báo khi tồn thực tế (SUM stock_lot.qty_remaining) < minStockQuantity.
         */
        java.math.BigDecimal minStockQuantity,

        // ── Cost ─────────────────────────────────────────────
        /**
         * Giá vốn per unit — chỉ nhập trực tiếp cho INGREDIENT.
         * SEMI_PRODUCT / PRODUCT: tính tự động từ công thức, field này bị bỏ qua.
         */
        BigDecimal unitCost,

        // ── Recipe (Product + SemiProduct) ───────────────────
        String recipeNote,

        /**
         * Tổng khối lượng sản phẩm BTP/SP tạo ra từ 1 mẻ (KG).
         * Dùng để tính giá/KG chính xác: giá/KG = tổng chi phí nguyên liệu / yieldQuantity.
         * null = tự tính từ tổng KG nguyên liệu trong công thức.
         */
        BigDecimal recipeYieldQuantity,

        @Valid List<RecipeLineRequest> recipeLines) {}
