package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Công thức nguyên liệu để làm 1 mẻ Phôi / Nhân.
 *
 * Cost 1 mẻ  = SUM(qty_in_batch * ingredient_price.price_per_kg)
 * cost_per_kg = cost 1 mẻ / semi_product.total_yield_kg
 *
 * Ví dụ — Nhân Xá Xíu (total_yield_kg = 1.054):
 *   THỊT XÁ XÍU  0.7 kg × 315,000 = 220,500
 *   NƯỚC          0.33 kg ×  10,000 =   3,300
 *   HÀNH TÂY      0.3 kg ×  15,000 =   4,500
 *   HÀNH PHI      0.1 kg × 190,000 =  19,000
 *   → tổng mẻ = 247,300 / 1.054 = 234,629 VND/kg
 */
@Entity
@Table(
    name = "recipe_line_semi",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_recipe_line_semi",
        columnNames = {"semi_product_id", "ingredient_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeLineSemi extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semi_product_id", nullable = false)
    private SemiProduct semiProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** KL nguyên liệu dùng trong 1 mẻ. Đơn vị KG hoặc QUẢ (trứng). */
    @Column(name = "qty_in_batch", nullable = false, precision = 12, scale = 4)
    private BigDecimal qtyInBatch;

    /** KG | QUA | ... */
    @Column(name = "unit", nullable = false, length = 20)
    @Builder.Default
    private String unit = "KG";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
