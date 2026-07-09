package com.bakery.framework.dto;

import java.time.Instant;
import java.util.UUID;

import com.bakery.framework.entity.BaseEntity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base class for all admin response DTOs.
 * Contains common audit + lifecycle fields.
 */
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseResponse {

    private UUID id;
    private String status;
    private String approvalStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private Instant approvedAt;
    private String approvedBy;
    private String rejectedReason;

    /**
     * Convenience method — copies all base fields from the entity.
     */
    public void applyFrom(BaseEntity entity) {
        this.id = entity.getId();
        this.status = entity.getStatus() != null ? entity.getStatus().name() : null;
        this.approvalStatus = entity.getApprovalStatus() != null ? entity.getApprovalStatus().name() : null;
        this.createdAt = entity.getCreatedAt();
        this.updatedAt = entity.getUpdatedAt();
        this.createdBy = entity.getCreatedBy();
        this.approvedAt = entity.getApprovedAt();
        this.approvedBy = entity.getApprovedBy();
        this.rejectedReason = entity.getRejectedReason();
    }
}
