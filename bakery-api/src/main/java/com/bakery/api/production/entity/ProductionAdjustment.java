package com.bakery.api.production.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.bakery.framework.entity.AdjustmentSource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.entity.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Điều chỉnh sản lượng thực tế so với plannedQty.
 *
 * <p>KITCHEN_COMPLETE: tạo tự động khi bếp bấm Complete với qtyProduced ≠ plannedQty.
 * <p>ADMIN_CORRECTION: admin tạo thủ công để sửa sau khi bếp đã submit.
 *
 * <p>Khi bếp trưởng APPROVE:
 *   INGREDIENT_VARIANCE → cộng/trừ NL kho bếp theo delta × recipe
 *   PRODUCTION_WASTAGE  → chỉ update DeliveryRecord.qtyProduced, không động NL
 */
@Getter
@Setter
@Entity
@Table(name = "production_adjustment")
public class ProductionAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_record_id", nullable = false)
    private DeliveryRecord deliveryRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 30)
    private AdjustmentType adjustmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private AdjustmentSource source;

    @Column(name = "original_qty", nullable = false, precision = 10, scale = 3)
    private BigDecimal originalQty;

    @Column(name = "adjusted_qty", nullable = false, precision = 10, scale = 3)
    private BigDecimal adjustedQty;

    /** adjustedQty - originalQty (âm = giảm, dương = tăng) */
    @Column(name = "delta", nullable = false, precision = 10, scale = 3)
    private BigDecimal delta;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING_APPROVAL;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
