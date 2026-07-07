package com.bakery.api.framework;

import com.bakery.api.framework.enums.EntityStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;

/**
 * Extends BaseEntity with admin lifecycle fields.
 * Use for master data entities that have approval workflow and soft-delete.
 * Tables: ingredient, product, supplier, purchase_order, recipe, etc.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAdminEntity extends BaseEntity {

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_status", nullable = false)
    private EntityStatus entityStatus = EntityStatus.ACTIVE;
}
