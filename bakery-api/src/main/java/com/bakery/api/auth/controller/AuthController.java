package com.bakery.api.auth.controller;

import java.util.Map;

import com.bakery.api.auth.dto.LoginRequest;
import com.bakery.api.auth.dto.LoginResponse;
import com.bakery.api.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints — public (không cần JWT).
 * SecurityConfig đã permit /api/v1/auth/**.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /api/v1/auth/login */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    /** POST /api/v1/auth/refresh — Body: { "refreshToken": "..." } */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(authService.refresh(token));
    }

    /** POST /api/v1/auth/logout — Body: { "refreshToken": "..." } */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token != null) authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
