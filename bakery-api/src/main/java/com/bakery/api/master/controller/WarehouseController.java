package com.bakery.api.master.controller;

import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.dto.WarehouseResponse;
import com.bakery.api.master.service.WarehouseService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController extends BakeryAdminResource<WarehouseRequest, WarehouseResponse> {

    private final WarehouseService service;

    @Override
    protected BakeryAdminService<WarehouseRequest, WarehouseResponse> getService() {
        return service;
    }
}
