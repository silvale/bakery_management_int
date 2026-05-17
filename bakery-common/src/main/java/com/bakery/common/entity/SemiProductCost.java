package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cost/kg của Phôi / Nhân theo version.
 *
 * Tính từ: SUM(recipe_line_semi.qty_in_batch * ingredient_price.price_per_kg)
 *          / semi_product.total_yield_kg
 *
 * Re-calculate và insert version mới mỗi khi giá nguyên liệu thay đổi.
 *
 * Lookup đúng cost tại ngày X:
 *   SELECT * FROM semi_product_cost
 *   WHERE semi_product_id = ? AND effective_date <= X
 *   ORDER BY effective_date DESC LIMIT 1
 */
@Entity
@Table(
    name = "semi_product_cost",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_semi_product_cost_version",
        columnNames = {"semi_product_id", "version"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemiProductCost extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semi_product_id", nullable = false)
    private SemiProduct semiProduct;

    /**
     * VND/kg.
     * Công thức: SUM(qty_in_batch * price_per_kg) / total_yield_kg
     */
    @Column(name = "cost_per_kg", nullable = false, precision = 18, scale = 4)
    private BigDecimal costPerKg;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
