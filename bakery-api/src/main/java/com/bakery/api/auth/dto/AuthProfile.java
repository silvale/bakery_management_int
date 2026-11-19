package com.bakery.api.auth.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/**
 * Hồ sơ phân quyền của user: trả kèm login và {@code GET /auth/me} (11/BE-2).
 * {@code permissionMap}: screen → các action được phép. Admin: mọi screen × mọi action đã đăng ký.
 */
public record AuthProfile(UUID userId, String username, String fullName, ReferenceValue role,
                          boolean isAdmin, List<RefInfo> warehouses, List<UUID> priceSupplierIds,
                          Map<String, List<String>> permissionMap) {}
