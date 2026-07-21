package com.bakery.api.auth.dto;

import java.util.UUID;

/**
 * Dùng cho cả CREATE và UPDATE.
 * - CREATE: password bắt buộc.
 * - UPDATE: nếu password null → giữ nguyên password cũ.
 */
public record UserAccountRequest(
        String username,
        String fullName,
        UUID roleId,
        /** Plaintext password — sẽ được BCrypt hash trong service. Null = giữ nguyên khi update. */
        String password
) {}
