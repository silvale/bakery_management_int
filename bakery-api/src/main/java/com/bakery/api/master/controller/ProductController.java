/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

// DEPRECATED — replaced by ItemController at /api/v1/items?itemType=PRODUCT
// Giữ lại class để tránh lỗi compile, nhưng không expose endpoint.
//
// @RestController
// @RequestMapping("/api/v1/products")

import com.bakery.api.master.dto.ProductRequest;
import com.bakery.api.master.dto.ProductResponse;
import com.bakery.api.master.service.ProductService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductController extends BakeryAdminResource<ProductRequest, ProductResponse> {

    private final ProductService service;

    @Override
    protected BakeryAdminService<ProductRequest, ProductResponse> getService() {
        return service;
    }
}
