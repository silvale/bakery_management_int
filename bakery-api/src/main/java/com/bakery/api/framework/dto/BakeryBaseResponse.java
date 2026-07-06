package com.bakery.api.framework.dto;

import com.bakery.common.entity.enums.EntityStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base response DTO — tất cả admin response đều extend class này.
 * Maps 1-1 với BaseEntity audit fields.
 */
@Getter
@Setter
public abstract class BakeryBaseResponse {

    private UUID id;

    private String createdBy;
    private OffsetDateTime createdAt;

    private String updatedBy;
    private OffsetDateTime updatedAt;

    private String approvedBy;
    private OffsetDateTime approvedAt;

    private EntityStatus entityStatus;
}
