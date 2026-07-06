package com.bakery.api.service;

import com.bakery.common.entity.RolePermission;
import com.bakery.common.entity.UserProfile;
import com.bakery.common.repository.RolePermissionRepository;
import com.bakery.common.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Kiểm tra quyền thao tác của user tại runtime.
 *
 * Dùng cho các action nhạy cảm cần guard backend (không chỉ ẩn UI):
 *   - Duyệt điều chỉnh kho (SCREEN_INVENTORY_ADJUSTMENT / can_approve) — chỉ anh Chính
 *   - Xem báo cáo chi phí (SCREEN_COST_REPORT / can_view)
 *   - ...
 *
 * FE ẩn nút → UX
 * BE check quyền → chống bypass bằng Postman/CURL
 */
@Service
@RequiredArgsConstructor
public class PermissionCheckerService {

    private final UserProfileRepository userProfileRepo;
    private final RolePermissionRepository permissionRepo;

    /** Kiểm tra user có can_approve trên 1 màn hình không. */
    public boolean canApprove(String username, String screenCode) {
        return checkFlag(username, screenCode, "approve");
    }

    /** Kiểm tra user có can_view trên 1 màn hình không. */
    public boolean canView(String username, String screenCode) {
        return checkFlag(username, screenCode, "view");
    }

    /** Kiểm tra user có can_create trên 1 màn hình không. */
    public boolean canCreate(String username, String screenCode) {
        return checkFlag(username, screenCode, "create");
    }

    /**
     * Throw 403 nếu user không có can_approve trên screenCode.
     * Dùng trong controller endpoint nhạy cảm:
     *
     *   permissionChecker.requireApprove(principal, "SCREEN_INVENTORY_ADJUSTMENT");
     */
    public void requireApprove(String username, String screenCode) {
        if (!canApprove(username, screenCode)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Từ chối truy cập: Tài khoản '" + username
                    + "' không có quyền phê duyệt tại màn hình " + screenCode
            );
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    private boolean checkFlag(String username, String screenCode, String action) {
        UserProfile user = userProfileRepo.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Không tìm thấy user: " + username));

        return permissionRepo.findByRoleIdWithScreen(user.getRole().getId())
            .stream()
            .filter(rp -> screenCode.equals(rp.getScreen().getCode()))
            .findFirst()
            .map(rp -> switch (action) {
                case "view"    -> rp.getCanView();
                case "create"  -> rp.getCanCreate();
                case "edit",
                     "update"  -> rp.getCanEdit();
                case "delete"  -> rp.getCanDelete();
                case "approve" -> rp.getCanApprove();
                default        -> false;
            })
            .orElse(false);
    }
}
