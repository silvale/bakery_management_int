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
    private final com.bakery.api.auth.repository.UserRoleRepository roleRepository;
    private final com.bakery.api.auth.service.RoleDefaultsService roleDefaults;

    @Override
    protected String screenCode() { return "ROLES"; }

    @Override
    protected BakeryAdminService<UserRoleRequest, UserRoleResponse> getService() {
        return service;
    }

    /** 11/BE-2: kho và NCC xem giá điền sẵn khi chọn vai trò này cho user. */
    @org.springframework.web.bind.annotation.GetMapping("/{id}/defaults")
    public com.bakery.api.auth.dto.RoleDefaults defaults(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        checkPermission("VIEW");
        return roleDefaults.defaults(roleRepository.findById(id)
                .orElseThrow(() -> new com.bakery.framework.exception.ResourceNotFoundException("UserRole", id)));
    }
}
