package com.bakery.api.auth.service;

import java.util.UUID;

import com.bakery.api.auth.repository.RolePermissionRepository;
import com.bakery.api.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra quyền truy cập theo role + screen + action.
 *
 * <p>SUPER_ADMIN luôn bypass mọi kiểm tra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Kiểm tra roleCode có quyền thực hiện action trên screen không.
     *
     * @param roleCode   mã role lấy từ JWT claim "role"
     * @param screenCode mã màn hình (VD: "ITEMS", "PROD_REQUESTS")
     * @param actionCode mã action (VD: "VIEW", "APPROVE", "DELETE")
     * @return true nếu được phép
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(String roleCode, String screenCode, String actionCode) {
        if (SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
            return true;
        }

        return userRoleRepository.findByCode(roleCode)
                .map(role -> rolePermissionRepository.existsByIdRoleIdAndIdScreenCodeAndIdActionCode(
                        role.getId(), screenCode, actionCode))
                .orElse(false);
    }

    /**
     * Overload tiện dụng khi đã có roleId (tránh lookup lại DB).
     */
    @Transactional(readOnly = true)
    public boolean hasPermissionById(String roleCode, UUID roleId, String screenCode, String actionCode) {
        if (SUPER_ADMIN.equalsIgnoreCase(roleCode)) {
            return true;
        }
        return rolePermissionRepository.existsByIdRoleIdAndIdScreenCodeAndIdActionCode(
                roleId, screenCode, actionCode);
    }
}
