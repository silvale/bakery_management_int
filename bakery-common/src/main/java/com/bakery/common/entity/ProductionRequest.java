package com.bakery.common.entity;

import com.bakery.common.entity.enums.EntityStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Đơn sản xuất — lệnh bếp làm một loại bánh cụ thể.
 *
 * Code: PRQ-{yyyyMMdd}-{seq}  VD: PRQ-20260701-001
 *
 * Loại:
 *   DAILY         → sinh từ ProductionPlan sau khi Chính duyệt
 *   CUSTOMER_ORDER → theo đơn khách đặt trước
 *   URGENT        → ad-hoc, bếp trưởng tạo thêm trong ngày
 *
 * Quy trình DAILY/URGENT:
 *   PENDING → bếp trưởng review/approve → IN_PRODUCTION
 *   → bếp báo qty_actual → COMPLETED
 *
 * Quy trình CUSTOMER_ORDER:
 *   PENDING → Chính approve → IN_PRODUCTION → bếp báo qty_actual → COMPLETED
 *
 * Nếu bếp dùng công thức tùy chỉnh cho đơn khách:
 *   recipe_id trỏ về custom recipe (is_custom = TRUE)
 *   price_override = giá bán mới nếu khác giá thông thường
 */
@Entity
@Table(name = "production_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    /** DAILY | CUSTOMER_ORDER | URGENT */
    @Column(name = "request_type", nullable = false, length = 20)
    @Builder.Default
    private String requestType = "DAILY";

    /** PENDING | APPROVED | REJECTED | IN_PRODUCTION | COMPLETED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** NULL nếu là ad-hoc URGENT hoặc CUSTOMER_ORDER không theo plan */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** NULL nếu dùng công thức tiêu chuẩn */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    /** Giá bán mới nếu custom recipe. NULL = dùng giá thông thường */
    @Column(name = "price_override", precision = 18, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "qty_planned", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyPlanned;

    /** Số lượng thực tế bếp báo sau khi làm xong. NULL = chưa hoàn thành */
    @Column(name = "qty_actual", precision = 12, scale = 3)
    private BigDecimal qtyActual;

    /** Lý do chênh lệch nếu qty_actual != qty_planned */
    @Column(name = "variance_reason", columnDefinition = "TEXT")
    private String varianceReason;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

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
}
