/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 1 dòng nguyên liệu / bán thành phẩm trong công thức.
 *
 * @param itemId    ID của Ingredient hoặc SemiProduct (item_type ≠ PRODUCT)
 * @param quantity  Số lượng cần dùng cho 1 đơn vị sản phẩm
 * @param unit      Đơn vị tính (kg, g, cái, …)
 * @param sortOrder Thứ tự hiển thị (tùy chọn)
 */
public record RecipeLineRequest(
        @NotNull UUID itemId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotBlank String unit,
        Integer sortOrder) {}
