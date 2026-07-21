package com.bakery.api.auth.dto;

import java.util.List;

/**
 * Body cho PUT /user-roles/{id}/permissions
 * Gửi toàn bộ danh sách permission (replace all).
 */
public record RolePermissionRequest(List<PermissionEntry> permissions) {

    public record PermissionEntry(String screenCode, String actionCode) {}
}
