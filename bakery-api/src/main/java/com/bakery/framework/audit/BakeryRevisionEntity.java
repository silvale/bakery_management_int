package com.bakery.framework.audit;

import java.time.Instant;

import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Custom Envers revision entity — lưu thêm actor và thời điểm thay đổi.
 * Tương ứng bảng {@code audit_revision_log} trong DB.
 */
@Entity
@Table(name = "audit_revision_log")
@RevisionEntity(BakeryRevisionListener.class)
@Getter
@Setter
public class BakeryRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "id")
    private long id;

    @RevisionTimestamp
    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    /** Username của người thực hiện thay đổi (lấy từ JWT SecurityContext). */
    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
