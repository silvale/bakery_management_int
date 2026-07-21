package com.bakery.api.auth.controller;

import com.bakery.api.auth.dto.UserRoleRequest;
import com.bakery.api.auth.dto.UserRoleResponse;
import com.bakery.api.auth.service.UserRoleService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-roles")
@RequiredArgsConstructor
public class UserRoleController extends BakeryAdminResource<UserRoleRequest, UserRoleResponse> {

    private final UserRoleService service;

    @Override
    protected String screenCode() { return "ROLES"; }

    @Override
    protected BakeryAdminService<UserRoleRequest, UserRoleResponse> getService() {
        return service;
    }
}
