/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.master.service.ItemService;
import com.bakery.api.recipe.dto.RecipeRequest;
import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.api.recipe.service.RecipeCostService;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý công thức sản phẩm (Recipe).
 *
 * <p>Framework endpoints (từ BakeryAdminResource):
 *   GET    /api/v1/recipes               → list + filter
 *   GET    /api/v1/recipes/all           → list không phân trang
 *   GET    /api/v1/recipes/{id}          → chi tiết
 *   POST   /api/v1/recipes               → tạo mới
 *   PUT    /api/v1/recipes/{id}          → cập nhật
 *   DELETE /api/v1/recipes/{id}          → xóa
 *   POST   /api/v1/recipes/{id}/approve  → duyệt
 *   POST   /api/v1/recipes/{id}/reject   → từ chối
 *
 * <p>Custom endpoints:
 *   POST   /api/v1/recipes/{id}/activate          → kích hoạt
 *   POST   /api/v1/recipes/{id}/clone             → nhân bản
 *   GET    /api/v1/recipes/by-product/{productId} → theo sản phẩm
 *   GET    /api/v1/recipes/by-semi/{semiProductId}→ theo bán thành phẩm
 *   GET    /api/v1/recipes/cost/{itemId}          → tính giá cost (preview, không lưu)
 *   POST   /api/v1/recipes/cost/{itemId}/apply    → tính + lưu vào item.unit_cost
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class RecipeController extends BakeryAdminResource<RecipeRequest, RecipeResponse> {

    private final RecipeService service;
    private final RecipeCostService recipeCostService;
    private final ItemService itemService;

    @Override
    protected BakeryAdminService<RecipeRequest, RecipeResponse> getService() {
        return service;
    }

    @PostMapping("/{id}/activate")
    public RecipeResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/clone")
    public RecipeResponse clone(@PathVariable UUID id) {
        return service.clone(id);
    }

    @GetMapping("/by-product/{productId}")
    public List<RecipeResponse> byProduct(@PathVariable UUID productId) {
        return service.findByProduct(productId);
    }

    @GetMapping("/by-semi/{semiProductId}")
    public List<RecipeResponse> bySemiProduct(@PathVariable UUID semiProductId) {
        return service.findBySemiProduct(semiProductId);
    }

    /**
     * Tính giá cost cho 1 SP / BTP — chỉ đọc, không lưu vào DB.
     * Trả về breakdown chi tiết từng nguyên liệu + nguồn giá (CATALOG/STOCK_LOT_AVG/MISSING).
     */
    @GetMapping("/cost/{itemId}")
    public RecipeCostService.CostResult calculateCost(@PathVariable UUID itemId) {
        return recipeCostService.calculate(itemId);
    }

    /**
     * Tính giá cost + lưu vào item.unit_cost.
     * Chỉ lưu khi complete=true (tất cả NL đều tìm được giá).
     * Luôn trả về CostResult để UI biết kết quả.
     */
    @PostMapping("/cost/{itemId}/apply")
    public RecipeCostService.CostResult applyCost(@PathVariable UUID itemId) {
        RecipeCostService.CostResult result = recipeCostService.calculate(itemId);
        if (result.complete()) {
            itemService.saveUnitCost(itemId, result.totalCostPerUnit());
        }
        return result;
    }
}
