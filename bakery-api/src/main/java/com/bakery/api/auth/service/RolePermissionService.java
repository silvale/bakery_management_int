package com.bakery.api.auth.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.auth.dto.RolePermissionRequest;
import com.bakery.api.auth.dto.ScreenResponse;
import com.bakery.api.auth.entity.RolePermission;
import com.bakery.api.auth.repository.RolePermissionRepository;
import com.bakery.api.auth.repository.ScreenRegistryRepository;
import com.bakery.api.auth.repository.UserRoleRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final ScreenRegistryRepository screenRegistryRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    /** Danh sách toàn bộ màn hình + available actions. */
    @Transactional(readOnly = true)
    public List<ScreenResponse> getAllScreens() {
        return screenRegistryRepository.findAllByOrderBySortOrderAsc()
                .stream().map(ScreenResponse::from).toList();
    }

    /**
     * Lấy permissions của một role — trả về map: screenCode → Set&lt;actionCode&gt;
     */
    @Transactional(readOnly = true)
    public Map<String, Set<String>> getPermissions(UUID roleId) {
        return rolePermissionRepository.findByIdRoleId(roleId)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getId().getScreenCode(),
                        Collectors.mapping(p -> p.getId().getActionCode(), Collectors.toSet())));
    }

    /**
     * Thay thế toàn bộ permissions của role (bulk replace).
     * Xóa cũ → insert mới.
     */
    @Transactional
    public Map<String, Set<String>> setPermissions(UUID roleId, RolePermissionRequest req) {
        // Validate role exists
        userRoleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("UserRole", roleId));

        // Load valid screen+action combos để validate
        var validActions = screenRegistryRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getCode(),
                        s -> s.getAvailableActionList()));

        // Delete all old permissions
        rolePermissionRepository.deleteAllByRoleId(roleId);

        // Insert new ones (validate each entry)
        List<RolePermission> toSave = req.permissions().stream()
                .filter(e -> {
                    var actions = validActions.get(e.screenCode());
                    return actions != null && actions.contains(e.actionCode());
                })
                .map(e -> new RolePermission(roleId, e.screenCode(), e.actionCode()))
                .distinct()
                .toList();

        rolePermissionRepository.saveAll(toSave);

        return getPermissions(roleId);
    }
}
