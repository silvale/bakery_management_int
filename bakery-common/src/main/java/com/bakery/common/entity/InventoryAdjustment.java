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
 * Phiếu điều chỉnh kiểm kê — khi tồn kho thực tế lệch với hệ thống.
 *
 * Code: ADJ-{yyyyMMdd}-{seq}  VD: ADJ-20260701-001
 *
 * Quy trình:
 *   1. Nhân viên kho kiểm kê → phát hiện chênh lệch
 *   2. Tạo phiếu (PENDING): chỉ rõ lô, qty_before (hệ thống), qty_after (thực tế)
 *   3. Chính duyệt (APPROVED) → hệ thống cập nhật qty_remaining của lô
 *   4. Ghi inventory_movement với reason = ADJUSTMENT_IN hoặc ADJUSTMENT_OUT
 *
 * qty_delta = qty_after - qty_before (computed column trong DB)
 *   > 0: tăng tồn (nhập adjustment)
 *   < 0: giảm tồn (xuất adjustment)
 *
 * item_type: INGREDIENT | PRODUCT
 * lot_id: FK vào ingredient_stock_lot.id hoặc product_stock_lot.id
 */
@Entity
@Table(name = "inventory_adjustment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** INGREDIENT | PRODUCT */
    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    /** Qty hiện tại trong hệ thống (trước điều chỉnh) */
    @Column(name = "qty_before", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyBefore;

    /** Qty thực tế đếm được */
    @Column(name = "qty_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyAfter;

    /**
     * Computed column từ DB: qty_after - qty_before.
     * insertable/updatable = false → Hibernate không write.
     */
    @Column(name = "qty_delta", insertable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal qtyDelta;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** PENDING | APPROVED | REJECTED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_status", nullable = false)
    @Builder.Default
    private EntityStatus entityStatus = EntityStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
