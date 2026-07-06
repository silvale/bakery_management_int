package com.bakery.api.dto.auth;

import java.util.Map;
import java.util.UUID;

/**
 * Response trả về sau khi đăng nhập thành công.
 *
 * permissions — map screenCode → PermissionEntry:
 * {
 *   "SCREEN_GOODS_TRANSFER": { "view": true, "create": true, ... }
 * }
 * FE dùng map này để ẩn/hiện button mà không cần gọi API phụ.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    UUID userId,
    String username,
    String fullName,
    String roleCode,
    String roleName,
    Map<String, PermissionEntry> permissions
) {
    /**
     * Quyền của user tại 1 màn hình cụ thể.
     * Tên field dùng camelCase để khớp JSON mà FE expect.
     */
    public record PermissionEntry(
        boolean view,
        boolean create,
        boolean update,   // alias cho can_edit ở DB
        boolean delete,
        boolean approve
    ) {}
}
