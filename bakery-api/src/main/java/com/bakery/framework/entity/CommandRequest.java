package com.bakery.framework.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit log for every state-changing operation across all admin entities.
 * One row per CREATE / UPDATE / DELETE / APPROVE / REJECT call.
 */
@Entity
@Table(name = "command_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommandRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Logical entity name, e.g. "Ingredient", "Product" */
    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    /** ID of the affected entity (null on CREATE before first save) */
    @Column(name = "entity_id")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private CommandAction action;

    /** Username / actor who triggered the command */
    @Column(name = "actor", length = 100)
    private String actor;

    /** Free-text note (e.g. rejection reason) */
    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommandStatus status;

    /** Short error detail when status = FAILED */
    @Column(name = "error_detail", length = 1000)
    private String errorDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
