package com.bakery.common.entity;

import com.bakery.common.entity.enums.CommandAction;
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
 * Append-only audit log cho mọi thay đổi entity.
 * Mỗi lần CREATE/UPDATE/DELETE (sau khi approved) → 1 row được insert.
 *
 * snapshot_before (JSONB): state của entity TRƯỚC khi thay đổi (null nếu CREATE).
 * snapshot_after  (JSONB): state của entity SAU khi thay đổi (null nếu DELETE).
 *
 * command_request_id: liên kết về command_request đã trigger thay đổi này.
 */
@Entity
@Table(
    name = "entity_revision_log",
    indexes = {
        @Index(name = "idx_rev_log_entity",  columnList = "entity_type, entity_id"),
        @Index(name = "idx_rev_log_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class EntityRevisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Tên entity (vd: "Ingredient", "IngredientPrice") */
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    /** UUID của entity trong bảng chính */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CommandAction action;

    /** UUID của CommandRequest đã trigger thay đổi này */
    @Column(name = "command_request_id")
    private UUID commandRequestId;

    /**
     * State của entity TRƯỚC thay đổi.
     * NULL nếu action = CREATE.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_before", columnDefinition = "jsonb")
    private String snapshotBefore;

    /**
     * State của entity SAU thay đổi.
     * NULL nếu action = DELETE.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_after", columnDefinition = "jsonb")
    private String snapshotAfter;

    // ── Audit (append-only, CreatedBy/Date only) ──────────────

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
