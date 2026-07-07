package com.bakery.api.modules.production.entities;

import com.bakery.api.framework.enums.EntityStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kế hoạch sản xuất ngày — tự động tạo sau khi reconcile xong.
 *
 * Mỗi ngày có đúng 1 ProductionPlan (UNIQUE trên plan_date).
 *
 * Quy trình:
 *   Reconcile hoàn thành → hệ thống auto-gen plan với qty từ template
 *                          trừ đi tồn kho hiện tại → status = PENDING
 *   Chính review + điều chỉnh qty_adjusted nếu cần → APPROVED
 *   Sau khi APPROVED → hệ thống tạo ProductionRequest cho từng line
 */
@Entity
@Table(name = "production_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "plan_date", nullable = false, unique = true)
    private LocalDate planDate;

    /**
     * PENDING  → chờ Chính duyệt
     * APPROVED → đã duyệt, ProductionRequest đã được tạo
     * REJECTED → bị từ chối (hiếm gặp)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_status", nullable = false)
    @Builder.Default
    private EntityStatus entityStatus = EntityStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductionPlanLine> lines = new ArrayList<>();
}
