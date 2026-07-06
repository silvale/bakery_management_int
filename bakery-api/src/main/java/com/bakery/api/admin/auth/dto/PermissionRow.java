package com.bakery.api.admin.auth.dto;

import com.bakery.common.entity.RolePermission;

import java.util.UUID;

/**
 * Một dòng phân quyền: màn hình + 5 flags.
 * Dùng cho cả GET (response) và PUT (request payload trong danh sách).
 */
public record PermissionRow(
    UUID screenId,
    String screenCode,
    String screenName,
    String module,
    boolean canView,
    boolean canCreate,
    boolean canEdit,
    boolean canDelete,
    boolean canApprove
) {
    public static PermissionRow from(RolePermission rp) {
        return new PermissionRow(
                rp.getScreen().getId(),
                rp.getScreen().getCode(),
                rp.getScreen().getName(),
                rp.getScreen().getModule(),
                rp.getCanView(),
                rp.getCanCreate(),
                rp.getCanEdit(),
                rp.getCanDelete(),
                rp.getCanApprove()
        );
    }
}
