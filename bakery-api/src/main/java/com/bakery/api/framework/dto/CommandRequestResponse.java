package com.bakery.api.framework.dto;

import com.bakery.common.entity.enums.CommandAction;
import com.bakery.common.entity.enums.CommandStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO cho CommandRequest — dùng trong tab Pending / Rejected.
 */
@Getter
@Setter
public class CommandRequestResponse {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private CommandAction action;
    private CommandStatus status;
    private String payload;
    private String note;

    private String createdBy;
    private OffsetDateTime createdAt;

    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String rejectReason;
}
