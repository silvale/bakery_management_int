package com.bakery.api.auth.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.auth.entity.RolePermission;
import com.bakery.api.auth.entity.RolePermission.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(UUID roleId);

    @Query("SELECT DISTINCT p.id.screenCode FROM RolePermission p WHERE p.id.roleId = :roleId AND p.id.screenCode = :screenCode AND p.id.actionCode = :actionCode")
    List<String> findMatchingAction(UUID roleId, String screenCode, String actionCode);

    boolean existsByIdRoleIdAndIdScreenCodeAndIdActionCode(UUID roleId, String screenCode, String actionCode);

    @Modifying
    @Query("DELETE FROM RolePermission p WHERE p.id.roleId = :roleId")
    void deleteAllByRoleId(UUID roleId);
}
