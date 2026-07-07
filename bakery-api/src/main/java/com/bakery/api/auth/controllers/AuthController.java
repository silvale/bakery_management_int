package com.bakery.api.auth.controllers;

import com.bakery.api.config.UserPrincipal;
import com.bakery.api.auth.dtos.LoginRequest;
import com.bakery.api.auth.dtos.LoginResponse;
import com.bakery.api.auth.dtos.RefreshRequest;
import com.bakery.api.auth.dtos.RefreshResponse;
import com.bakery.api.auth.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Đăng nhập / Đăng xuất / Refresh token")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/login
     * Body: { "username": "admin", "password": "Admin@123" }
     */
    @PostMapping("/login")
    @Operation(summary = "Đăng nhập")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /auth/refresh
     * Body: { "refreshToken": "..." }
     */
    @PostMapping("/refresh")
    @Operation(summary = "Làm mới access token")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * POST /auth/logout
     * Header: Authorization: Bearer <access_token>
     */
    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất — revoke tất cả refresh token")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal != null ? principal.username() : null);
        return ResponseEntity.noContent().build();
    }
}
