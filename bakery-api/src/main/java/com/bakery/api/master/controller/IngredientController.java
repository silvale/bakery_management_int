/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

// DEPRECATED — replaced by ItemController at /api/v1/items?itemType=INGREDIENT
// Giữ lại class để tránh lỗi compile nếu có nơi nào inject, nhưng không expose endpoint.
//
// @RestController
// @RequestMapping("/api/v1/ingredients")

import com.bakery.api.master.dto.IngredientRequest;
import com.bakery.api.master.dto.IngredientResponse;
import com.bakery.api.master.service.IngredientService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IngredientController extends BakeryAdminResource<IngredientRequest, IngredientResponse> {

    private final IngredientService service;

    @Override
    protected BakeryAdminService<IngredientRequest, IngredientResponse> getService() {
        return service;
    }
}
