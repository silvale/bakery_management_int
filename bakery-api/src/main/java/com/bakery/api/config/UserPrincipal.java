package com.bakery.api.config;

import java.util.UUID;

/**
 * Principal object đặt trong SecurityContext sau khi JWT hợp lệ.
 *
 * <ul>
 *   <li>{@code username}  — tên đăng nhập (subject của JWT)</li>
 *   <li>{@code roleCode}  — role code (VD: SUPER_ADMIN, KHO_TRUONG, BEP_TRUONG)</li>
 *   <li>{@code branchId}  — UUID branch của user; {@code null} nếu không giới hạn scope</li>
 * </ul>
 *
 * Dùng trong controller qua {@code @AuthenticationPrincipal UserPrincipal principal}.
 *
 * <pre>
 * // Kiểm tra có cần filter theo branch không:
 * boolean scopeLimited = principal.isScopeLimited();
 * </pre>
 */
public record UserPrincipal(String username, String roleCode, UUID branchId) {

    /** Roles không bị giới hạn scope — xem toàn bộ dữ liệu mọi branch. */
    private static final java.util.Set<String> GLOBAL_ROLES = java.util.Set.of(
        "SUPER_ADMIN", "KHO_TRUONG"
    );

    /**
     * {@code true} nếu user bị giới hạn theo branch (BEP_TRUONG, BEP_VIEN, NHAN_VIEN_BH).
     * {@code false} nếu là SUPER_ADMIN hoặc KHO_TRUONG.
     */
    public boolean isScopeLimited() {
        return !GLOBAL_ROLES.contains(roleCode) && branchId != null;
    }
}
