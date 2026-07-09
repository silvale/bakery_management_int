package com.bakery.api.master.controller;

import com.bakery.api.master.dto.SemiProductRequest;
import com.bakery.api.master.dto.SemiProductResponse;
import com.bakery.api.master.service.SemiProductService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/semi-products")
@RequiredArgsConstructor
public class SemiProductController extends BakeryAdminResource<SemiProductRequest, SemiProductResponse> {

    private final SemiProductService service;

    @Override
    protected BakeryAdminService<SemiProductRequest, SemiProductResponse> getService() {
        return service;
    }
}
