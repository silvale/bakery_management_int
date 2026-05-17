package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Log chi tiết mỗi lần FIFO engine phân bổ nguyên liệu.
 * Dùng để audit và recalculate khi backdate nhập kho.
 */
@Entity
@Table(name = "fifo_allocation_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FifoAllocationLog extends BaseLogEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_lot_id", nullable = false)
    private ProductionLot productionLot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_stock_lot_id", nullable = false)
    private IngredientStockLot ingredientStockLot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Số lượng gram/ml đã trừ từ lô nguyên liệu này */
    @Column(name = "qty_allocated", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyAllocated;

    /** Giá tại thời điểm phân bổ (VNĐ/gram) */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitPrice;

    /** = qty_allocated * unit_price. Đóng góp vào cost_per_unit của lô bánh */
    @Column(name = "cost_contribution", nullable = false, precision = 18, scale = 6)
    private BigDecimal costContribution;

    /** TRUE nếu đã recalculate sau backdate */
    @Column(name = "is_recalculated", nullable = false)
    @Builder.Default
    private Boolean isRecalculated = false;
}
