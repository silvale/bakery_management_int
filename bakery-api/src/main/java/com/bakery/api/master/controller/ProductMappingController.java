package com.bakery.api.master.controller;

import com.bakery.api.master.dto.ProductMappingRequest;
import com.bakery.api.master.dto.ProductMappingResponse;
import com.bakery.api.master.service.ProductMappingService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-mappings")
@RequiredArgsConstructor
public class ProductMappingController
        extends BakeryAdminResource<ProductMappingRequest, ProductMappingResponse> {

    private final ProductMappingService service;

    @Override
    protected String screenCode() { return "PRODUCT_MAPPING"; }

    @Override
    protected BakeryAdminService<ProductMappingRequest, ProductMappingResponse> getService() {
        return service;
    }
}
