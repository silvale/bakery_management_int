package com.bakery.api.master.controller;

import com.bakery.api.master.dto.StoreRequest;
import com.bakery.api.master.dto.StoreResponse;
import com.bakery.api.master.service.StoreService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 23/BE-1: cửa hàng; quyền theo màn Quản lý kho (WAREHOUSES). */
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController extends BakeryAdminResource<StoreRequest, StoreResponse> {

    private final StoreService service;

    @Override
    protected String screenCode() { return "WAREHOUSES"; }

    @Override
    protected BakeryAdminService<StoreRequest, StoreResponse> getService() { return service; }
}
