package com.bakery.common.entity;

import com.bakery.common.entity.enums.CommandAction;
import com.bakery.common.entity.enums.CommandStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lưu trữ các yêu cầu thay đổi chờ admin duyệt.
 *
 * Flow:
 *   User submit → PENDING
 *   Admin approve → APPROVED → execute vào bảng chính
 *   Admin reject  → REJECTED (không execute)
 *
 * payload (JSONB): snapshot JSON của request body (DTO trước khi execute).
 * entity_id: UUID của entity trong bảng chính (NULL nếu action = CREATE, fill sau khi approved).
 * entity_type: tên entity (vd: "Ingredient", "IngredientPrice").
 */
@Entity
@Table(
    name = "command_request",
    indexes = {
        @Index(name = "idx_cmd_req_entity",  columnList = "entity_type, entity_id"),
        @Index(name = "idx_cmd_req_status",  columnList = "status"),
        @Index(name = "idx_cmd_req_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CommandRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Tên entity (vd: "Ingredient", "IngredientPrice", "SemiProduct") */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** UUID của entity trong bảng chính. NULL khi action=CREATE, fill sau khi approve. */
    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CommandAction action;

    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CommandStatus status = CommandStatus.PENDING;

    /**
     * Snapshot JSON của request body.
     * Dùng để execute khi approve, hoặc hiển thị diff cho admin.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    /** Mô tả lý do thay đổi (optional, nhập bởi user) */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // ── Audit ─────────────────────────────────────────────────

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Người duyệt / từ chối */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    /** Lý do từ chối (nếu REJECTED) */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;
}
