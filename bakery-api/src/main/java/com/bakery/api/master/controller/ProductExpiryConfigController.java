package com.bakery.api.master.controller;

import com.bakery.api.master.dto.ProductExpiryConfigRequest;
import com.bakery.api.master.dto.ProductExpiryConfigResponse;
import com.bakery.api.master.service.ProductExpiryConfigService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-expiry-configs")
@RequiredArgsConstructor
public class ProductExpiryConfigController extends BakeryAdminResource<ProductExpiryConfigRequest, ProductExpiryConfigResponse> {

    private final ProductExpiryConfigService service;

    @Override
    protected BakeryAdminService<ProductExpiryConfigRequest, ProductExpiryConfigResponse> getService() {
        return service;
    }
}
