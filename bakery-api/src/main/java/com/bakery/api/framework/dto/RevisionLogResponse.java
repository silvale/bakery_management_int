package com.bakery.api.framework.dto;

import com.bakery.common.entity.enums.CommandAction;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO cho EntityRevisionLog — dùng trong history panel.
 */
@Getter
@Setter
public class RevisionLogResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private CommandAction action;
    private UUID commandRequestId;
    private String snapshotBefore;
    private String snapshotAfter;
    private String createdBy;
    private OffsetDateTime createdAt;
}
