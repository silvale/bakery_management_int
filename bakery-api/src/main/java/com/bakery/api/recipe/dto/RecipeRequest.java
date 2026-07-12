/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Tạo / cập nhật công thức.
 *
 * <p>Đúng một trong hai field (productId / semiProductId) phải có giá trị.
 * Validation biz-rule này được kiểm tra trong service.
 *
 * @param productId     ID sản phẩm (item_type = PRODUCT)
 * @param semiProductId ID bán thành phẩm (item_type = SEMI_PRODUCT)
 * @param note          Ghi chú công thức
 * @param lines         Danh sách nguyên liệu / bán thành phẩm cần dùng
 */
public record RecipeRequest(
        UUID productId,
        UUID semiProductId,
        String note,
        @NotEmpty @Valid List<RecipeLineRequest> lines) {}
