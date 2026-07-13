/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.service.ItemService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified Item API — thay thế các controller riêng:
 *   /api/v1/ingredients    → dùng ?itemType=INGREDIENT
 *   /api/v1/semi-products  → dùng ?itemType=SEMI_PRODUCT
 *   /api/v1/products       → dùng ?itemType=PRODUCT
 *   (không có filter)      → load tất cả mọi loại
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController extends BakeryAdminResource<ItemRequest, ItemResponse> {

    private final ItemService service;

    @Override
    protected BakeryAdminService<ItemRequest, ItemResponse> getService() {
        return service;
    }
}
