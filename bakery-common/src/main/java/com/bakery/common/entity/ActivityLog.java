package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Audit trail — ghi nhận mọi hành động nghiệp vụ của người dùng.
 *
 * Mọi action quan trọng (approve/reject/confirm/adjust) đều phải
 * ghi 1 record vào bảng này để Chính có thể kiểm tra lịch sử.
 *
 * action examples:
 *   APPROVE_TRANSFER, REJECT_TRANSFER
 *   APPROVE_PLAN, REJECT_PLAN
 *   CONFIRM_DELIVERY, REJECT_DELIVERY
 *   APPROVE_WRITE_OFF, APPROVE_ADJUSTMENT
 *   CREATE_PRODUCTION_REQUEST, COMPLETE_PRODUCTION
 *   CREATE_ORDER, CANCEL_ORDER
 *   RECORD_PAYMENT
 *
 * entity_type: GoodsTransfer | ProductionPlan | ProductionRequest |
 *              StockTransfer | InventoryWriteOff | InventoryAdjustment |
 *              CustomerOrder | SupplierReturn | GoodsTransferReturn
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** Mã dễ đọc: TRF-001, PRQ-001, ORD-001 */
    @Column(name = "entity_code", length = 100)
    private String entityCode;

    /** Trạng thái trước action */
    @Column(name = "old_status", length = 50)
    private String oldStatus;

    /** Trạng thái sau action */
    @Column(name = "new_status", length = 50)
    private String newStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
