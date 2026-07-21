package com.bakery.api.auth.controller;

import java.util.Map;
import java.util.UUID;

import com.bakery.api.auth.dto.UserAccountRequest;
import com.bakery.api.auth.dto.UserAccountResponse;
import com.bakery.api.auth.service.UserAccountService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-accounts")
@RequiredArgsConstructor
public class UserAccountController extends BakeryAdminResource<UserAccountRequest, UserAccountResponse> {

    private final UserAccountService service;

    @Override
    protected String screenCode() { return "USERS"; }

    @Override
    protected BakeryAdminService<UserAccountRequest, UserAccountResponse> getService() {
        return service;
    }

    /**
     * Đổi mật khẩu.
     * POST /api/v1/user-accounts/{id}/change-password
     * Body: { "newPassword": "..." }
     */
    @PostMapping("/{id}/change-password")
    public ResponseEntity<UserAccountResponse> changePassword(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        checkPermission("UPDATE");
        String newPassword = body.get("newPassword");
        return ResponseEntity.ok(service.changePassword(id, newPassword));
    }
}
