package com.bakery.common.repository;

import com.bakery.common.entity.RolePermission;
import com.bakery.common.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByIdRoleId(UUID roleId);

    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.screen WHERE rp.id.roleId = :roleId")
    List<RolePermission> findByRoleIdWithScreen(@Param("roleId") UUID roleId);

    @Query("""
        SELECT rp FROM RolePermission rp
        JOIN FETCH rp.screen s
        WHERE rp.id.roleId = :roleId AND s.code = :screenCode
        """)
    java.util.Optional<RolePermission> findByRoleIdAndScreenCode(
        @Param("roleId") UUID roleId,
        @Param("screenCode") String screenCode
    );

    void deleteByIdRoleId(UUID roleId);
}
