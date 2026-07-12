/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.recipe.dto.RecipeRequest;
import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý công thức sản phẩm (Recipe).
 *
 * <p>Framework endpoints (từ BakeryAdminResource):
 *   GET    /api/v1/recipes               → list + filter (params: productId, semiProductId, approvalStatus, ...)
 *   GET    /api/v1/recipes/all           → list không phân trang
 *   GET    /api/v1/recipes/{id}          → chi tiết
 *   POST   /api/v1/recipes               → tạo mới (PENDING_APPROVAL, is_active=false)
 *   PUT    /api/v1/recipes/{id}          → cập nhật (chỉ khi chưa APPROVED)
 *   DELETE /api/v1/recipes/{id}          → xóa
 *   POST   /api/v1/recipes/{id}/approve  → duyệt → APPROVED
 *   POST   /api/v1/recipes/{id}/reject   → từ chối
 *
 * <p>Custom endpoints:
 *   POST   /api/v1/recipes/{id}/activate          → kích hoạt (deactivate recipe cũ)
 *   POST   /api/v1/recipes/{id}/clone             → nhân bản (version mới, PENDING_APPROVAL)
 *   GET    /api/v1/recipes/by-product/{productId} → tất cả version của 1 sản phẩm
 *   GET    /api/v1/recipes/by-semi/{semiProductId}→ tất cả version của 1 bán thành phẩm
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class RecipeController extends BakeryAdminResource<RecipeRequest, RecipeResponse> {

    private final RecipeService service;

    @Override
    protected BakeryAdminService<RecipeRequest, RecipeResponse> getService() {
        return service;
    }

    /**
     * Kích hoạt recipe → is_active=true.
     * Các recipe khác của cùng SP/SemiProduct bị deactivate tự động.
     * Chỉ APPROVED recipe mới được activate.
     */
    @PostMapping("/{id}/activate")
    public RecipeResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    /**
     * Nhân bản recipe → bản sao mới với version tăng thêm 1, trạng thái PENDING_APPROVAL.
     * parentRecipeId của bản clone trỏ về recipe gốc.
     */
    @PostMapping("/{id}/clone")
    public RecipeResponse clone(@PathVariable UUID id) {
        return service.clone(id);
    }

    /**
     * Tất cả phiên bản công thức của 1 sản phẩm, sắp xếp theo version DESC (mới nhất trước).
     */
    @GetMapping("/by-product/{productId}")
    public List<RecipeResponse> byProduct(@PathVariable UUID productId) {
        return service.findByProduct(productId);
    }

    /**
     * Tất cả phiên bản công thức của 1 bán thành phẩm.
     */
    @GetMapping("/by-semi/{semiProductId}")
    public List<RecipeResponse> bySemiProduct(@PathVariable UUID semiProductId) {
        return service.findBySemiProduct(semiProductId);
    }
}
