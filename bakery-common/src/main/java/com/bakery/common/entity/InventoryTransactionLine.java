package com.bakery.common.entity;

import com.bakery.common.entity.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dòng chi tiết của phiếu kho.
 *
 * item_id là polymorphic FK: trỏ tới ingredient.id hoặc product.id tùy item_type.
 * lot_id tham chiếu Inventory.id — row lô được deduct khi FEFO xuất kho.
 *
 * qty_requested — số lượng yêu cầu (tại thời điểm tạo phiếu)
 * qty_approved  — số lượng thực tế được duyệt (có thể nhỏ hơn yêu cầu)
 */
@Entity
@Table(name = "inventory_transaction_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private InventoryTransaction transaction;

    /**
     * Polymorphic FK — không có DB-level FK constraint.
     */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @Column(name = "qty_requested", nullable = false, precision = 12, scale = 4)
    private BigDecimal qtyRequested;

    @Column(name = "qty_approved", precision = 12, scale = 4)
    private BigDecimal qtyApproved;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /**
     * Lô kho được gắn khi approve (FEFO deduction).
     * Tham chiếu Inventory.id — null trước khi approve.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Inventory lot;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
