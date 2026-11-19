package com.bakery.api.cake.controller;

import com.bakery.api.cake.dto.CakeDesignRequest;
import com.bakery.api.cake.dto.CakeDesignResponse;
import com.bakery.api.cake.service.CakeDesignService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cake-designs")
@RequiredArgsConstructor
public class CakeDesignController extends BakeryAdminResource<CakeDesignRequest, CakeDesignResponse> {

    private final CakeDesignService service;

    @Override protected BakeryAdminService<CakeDesignRequest, CakeDesignResponse> getService() { return service; }
    @Override protected String screenCode() { return "CAKE_DESIGN"; }
}
