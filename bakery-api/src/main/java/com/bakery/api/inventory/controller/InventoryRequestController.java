package com.bakery.api.inventory.controller;

import com.bakery.api.inventory.dto.InventoryRequestRequest;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.service.InventoryRequestService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory-requests")
@RequiredArgsConstructor
public class InventoryRequestController
        extends BakeryAdminResource<InventoryRequestRequest, InventoryRequestResponse> {

    private final InventoryRequestService service;

    @Override
    protected BakeryAdminService<InventoryRequestRequest, InventoryRequestResponse> getService() {
        return service;
    }
}
