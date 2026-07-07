package com.bakery.api.auth.dtos;

import com.bakery.api.auth.entities.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    String code,
    String name,
    String description,
    Boolean isActive,
    OffsetDateTime createdAt
) {
    public static RoleResponse from(UserRole r) {
        return new RoleResponse(r.getId(), r.getCode(), r.getName(),
                r.getDescription(), r.getIsActive(), r.getCreatedAt());
    }
}
