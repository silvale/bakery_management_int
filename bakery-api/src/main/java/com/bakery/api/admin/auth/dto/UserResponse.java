package com.bakery.api.admin.auth.dto;

import com.bakery.common.entity.UserProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    Boolean isActive,
    UUID roleId,
    String roleCode,
    String roleName,
    UUID branchId,
    String branchCode,
    String branchName,
    OffsetDateTime createdAt
) {
    public static UserResponse from(UserProfile u) {
        return new UserResponse(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                u.getIsActive(),
                u.getRole().getId(), u.getRole().getCode(), u.getRole().getName(),
                u.getBranch() != null ? u.getBranch().getId()   : null,
                u.getBranch() != null ? u.getBranch().getCode() : null,
                u.getBranch() != null ? u.getBranch().getName() : null,
                u.getCreatedAt()
        );
    }
}
