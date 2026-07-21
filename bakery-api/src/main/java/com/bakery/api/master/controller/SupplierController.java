package com.bakery.api.master.controller;

import com.bakery.api.master.dto.SupplierRequest;
import com.bakery.api.master.dto.SupplierResponse;
import com.bakery.api.master.service.SupplierService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController extends BakeryAdminResource<SupplierRequest, SupplierResponse> {

    private final SupplierService service;

    @Override
    protected String screenCode() { return "SUPPLIERS"; }

    @Override
    protected BakeryAdminService<SupplierRequest, SupplierResponse> getService() {
        return service;
    }
}
