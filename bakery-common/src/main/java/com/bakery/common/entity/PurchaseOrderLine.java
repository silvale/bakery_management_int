package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Chi tiết từng mục trong đơn nhập hàng.
 *
 * Mỗi line là INGREDIENT hoặc PRODUCT (accessory) — không thể cả hai.
 * Constraint chk_pol_item đảm bảo tính toàn vẹn ở DB level.
 *
 * qty_in_base_unit: chỉ dùng với INGREDIENT, NULL với PRODUCT.
 */
@Entity
@Table(name = "purchase_order_line")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    /** NULL nếu line này là accessory (product_id sẽ có giá trị) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    /** NULL nếu line này là nguyên liệu (ingredient_id sẽ có giá trị) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** Đơn vị mua: BAO_25KG, THUNG_20L, KG, QUA... */
    @Column(name = "purchase_unit", nullable = false, length = 50)
    private String purchaseUnit;

    /** Số lượng đặt (theo purchase_unit) */
    @Column(name = "qty_ordered", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyOrdered;

    /** Số lượng thực nhận (có thể khác đặt). NULL = chưa nhận */
    @Column(name = "qty_received", precision = 12, scale = 3)
    private BigDecimal qtyReceived;

    /** Đơn giá thực tế VNĐ / purchase_unit */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Computed column từ DB: COALESCE(qty_received, qty_ordered) * unit_price
     * insertable/updatable = false
     */
    @Column(name = "total_price", insertable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Số lượng quy đổi về base_unit (gram/ml).
     * Tính khi nhận hàng: qty_received * UnitConversion.base_quantity
     */
    @Column(name = "qty_in_base_unit", precision = 18, scale = 4)
    private BigDecimal qtyInBaseUnit;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
