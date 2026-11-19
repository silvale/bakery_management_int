package com.bakery.api.auth.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/** Kết quả đăng nhập/refresh: token + hồ sơ phân quyền (cùng field với {@code GET /auth/me}, 11/BE-2). */
public record LoginResponse(
        UUID userId,
        String username,
        String fullName,
        UUID roleId,
        String roleCode,
        String roleName,
        String accessToken,
        String refreshToken,
        ReferenceValue role,
        boolean isAdmin,
        List<RefInfo> warehouses,
        List<UUID> priceSupplierIds,
        Map<String, List<String>> permissionMap
) {
    public static LoginResponse of(UUID userId, String username, String fullName, UUID roleId, String roleCode,
                                   String roleName, String accessToken, String refreshToken, AuthProfile p) {
        return new LoginResponse(userId, username, fullName, roleId, roleCode, roleName, accessToken, refreshToken,
                p.role(), p.isAdmin(), p.warehouses(), p.priceSupplierIds(), p.permissionMap());
    }
}
