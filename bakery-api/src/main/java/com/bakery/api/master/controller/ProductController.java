package com.bakery.api.master.controller;

import com.bakery.api.master.dto.ProductRequest;
import com.bakery.api.master.dto.ProductResponse;
import com.bakery.api.master.service.ProductService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController extends BakeryAdminResource<ProductRequest, ProductResponse> {

    private final ProductService service;

    @Override
    protected BakeryAdminService<ProductRequest, ProductResponse> getService() {
        return service;
    }
}
