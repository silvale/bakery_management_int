package com.bakery.api.auth.dto;

import java.util.UUID;

public record LoginResponse(
        UUID userId,
        String username,
        String fullName,
        UUID roleId,
        String roleCode,
        String roleName,
        String accessToken,
        String refreshToken
) {}
