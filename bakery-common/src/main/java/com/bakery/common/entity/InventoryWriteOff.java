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
 * Phiếu xử lý hàng hỏng / hết hạn tại bất kỳ kho.
 *
 * Code: WOF-{yyyyMMdd}-{seq}  VD: WOF-20260701-001
 *
 * Quy trình:
 *   1. Nhân viên kho tạo phiếu (PENDING), chỉ rõ lô và lý do.
 *   2. Chính duyệt (APPROVED) → hệ thống trừ qty_remaining khỏi lot.
 *   3. Ghi inventory_movement với reason = WRITE_OFF.
 *   4. Nếu lý do là NCC giao hàng hỏng → tạo SupplierReturn kèm.
 *
 * item_type: INGREDIENT | PRODUCT (accessories)
 * lot_id: FK vào ingredient_stock_lot.id hoặc product_stock_lot.id
 * (không thể làm FK thực vì 2 bảng khác nhau — validate ở service layer)
 */
@Entity
@Table(name = "inventory_write_off")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryWriteOff {

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

    /** ID của lô bị xử lý (ingredient_stock_lot hoặc product_stock_lot) */
    @Column(name = "lot_id", nullable = false)
    private UUID lotId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /** EXPIRED | DAMAGED | MOLD | OTHER */
    @Column(name = "reason_type", nullable = false, length = 20)
    private String reasonType;

    @Column(name = "reason_note", columnDefinition = "TEXT")
    private String reasonNote;

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
