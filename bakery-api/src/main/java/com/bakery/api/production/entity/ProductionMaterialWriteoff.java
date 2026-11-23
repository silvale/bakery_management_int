package com.bakery.api.production.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 20/D6: hao hụt NL còn dư trong bảng tạm — có lý do (ADJUSTMENT_REASON) và người ghi. Không tạo movement. */
@Getter
@Setter
@Entity
@Table(name = "production_material_writeoff")
public class ProductionMaterialWriteoff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "production_material_id", nullable = false, updatable = false)
    private UUID productionMaterialId;

    @Column(name = "qty", nullable = false, precision = 15, scale = 4)
    private BigDecimal qty;

    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Column(name = "note", length = 500)
    private String note;

    /** qty × unit_cost của lô nguồn; null nếu lô không có giá vốn. */
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
