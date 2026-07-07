package com.bakery.api.auth.services;

import com.bakery.api.auth.dtos.PermissionRow;
import com.bakery.api.auth.dtos.RoleRequest;
import com.bakery.api.auth.dtos.RoleResponse;
import com.bakery.api.auth.entities.RolePermission;
import com.bakery.api.auth.entities.RolePermissionId;
import com.bakery.api.auth.entities.ScreenRegistry;
import com.bakery.api.auth.entities.UserRole;
import com.bakery.api.auth.repositories.RolePermissionRepository;
import com.bakery.api.auth.repositories.ScreenRegistryRepository;
import com.bakery.api.auth.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final UserRoleRepository roleRepo;
    private final RolePermissionRepository permissionRepo;
    private final ScreenRegistryRepository screenRepo;

    // ── Roles ──────────────────────────────────────────────────────

    public List<RoleResponse> listAll() {
        return roleRepo.findAll().stream().map(RoleResponse::from).toList();
    }

    public RoleResponse getById(UUID id) {
        return RoleResponse.from(findRole(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepo.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role code đã tồn tại: " + request.code());
        }
        UserRole role = UserRole.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .isActive(true)
                .build();
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        UserRole role = findRole(id);

        // Code chỉ được đổi nếu unique
        if (!role.getCode().equals(request.code()) && roleRepo.existsByCode(request.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Role code đã tồn tại: " + request.code());
        }

        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setUpdatedAt(OffsetDateTime.now());
        return RoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public void deactivate(UUID id) {
        UserRole role = findRole(id);
        role.setIsActive(false);
        role.setUpdatedAt(OffsetDateTime.now());
        roleRepo.save(role);
    }

    // ── Permissions ────────────────────────────────────────────────

    public List<PermissionRow> getPermissions(UUID roleId) {
        findRole(roleId); // validate exists
        return permissionRepo.findByRoleIdWithScreen(roleId)
                .stream().map(PermissionRow::from).toList();
    }

    /**
     * PUT /admin/roles/{roleId}/permissions
     * Thay toàn bộ permission của role bằng danh sách mới.
     * Chỉ cần gửi các field muốn set — screenId là bắt buộc.
     */
    @Transactional
    public List<PermissionRow> savePermissions(UUID roleId, List<PermissionRow> rows) {
        UserRole role = findRole(roleId);

        // Load tất cả screens để validate screenId
        Map<UUID, ScreenRegistry> screenMap = screenRepo.findAll()
                .stream().collect(Collectors.toMap(ScreenRegistry::getId, Function.identity()));

        // Xoá cũ
        permissionRepo.deleteByIdRoleId(roleId);

        // Insert mới
        List<RolePermission> permissions = rows.stream().map(row -> {
            ScreenRegistry screen = screenMap.get(row.screenId());
            if (screen == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Screen không tồn tại: " + row.screenId());
            }
            return RolePermission.builder()
                    .id(new RolePermissionId(roleId, screen.getId()))
                    .role(role)
                    .screen(screen)
                    .canView(row.canView())
                    .canCreate(row.canCreate())
                    .canEdit(row.canEdit())
                    .canDelete(row.canDelete())
                    .canApprove(row.canApprove())
                    .build();
        }).toList();

        permissionRepo.saveAll(permissions);

        return permissionRepo.findByRoleIdWithScreen(roleId)
                .stream().map(PermissionRow::from).toList();
    }

    // ── Internal ───────────────────────────────────────────────────

    private UserRole findRole(UUID id) {
        return roleRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Role không tồn tại: " + id));
    }
}
