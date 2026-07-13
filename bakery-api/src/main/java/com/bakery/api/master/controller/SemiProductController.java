/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

// DEPRECATED — replaced by ItemController at /api/v1/items?itemType=SEMI_PRODUCT
// Giữ lại class để tránh lỗi compile, nhưng không expose endpoint.
//
// @RestController
// @RequestMapping("/api/v1/semi-products")

import com.bakery.api.master.dto.SemiProductRequest;
import com.bakery.api.master.dto.SemiProductResponse;
import com.bakery.api.master.service.SemiProductService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SemiProductController extends BakeryAdminResource<SemiProductRequest, SemiProductResponse> {

    private final SemiProductService service;

    @Override
    protected BakeryAdminService<SemiProductRequest, SemiProductResponse> getService() {
        return service;
    }
}
