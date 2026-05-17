package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Chi tiết từng sản phẩm trong lệnh sản xuất.
 *
 * qty_requested → Admin điền trong BanhRaNgay.xlsx (cột "dự kiến")
 * qty_actual    → Bếp điền trong XuatRa.xlsx (cột "thực tế")
 *                 Batch cập nhật khi đọc file XuatRa.
 */
@Entity
@Table(
    name = "production_order_line",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_production_order_line",
        columnNames = {"order_id", "product_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Số lượng Admin yêu cầu (BanhRaNgay.xlsx - cột dự kiến) */
    @Column(name = "qty_requested", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyRequested;

    /** Số lượng Bếp thực tế làm ra (XuatRa.xlsx - cột thực tế). NULL = chưa có */
    @Column(name = "qty_actual", precision = 12, scale = 3)
    private BigDecimal qtyActual;

    /** PCS | KG */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;
}
