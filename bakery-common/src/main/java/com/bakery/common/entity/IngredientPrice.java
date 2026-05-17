package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Giá nhập nguyên liệu theo version.
 *
 * Lookup đúng giá tại ngày X:
 *   SELECT * FROM ingredient_price
 *   WHERE ingredient_id = ? AND effective_date <= X
 *   ORDER BY effective_date DESC LIMIT 1
 *
 * cost_per_gram = price_per_kg / 1000
 */
@Entity
@Table(
    name = "ingredient_price",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ingredient_price_version",
        columnNames = {"ingredient_id", "version"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientPrice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Giá VND/kg (hoặc VND/L nếu base_unit = ML). cost_per_gram = price_per_kg / 1000 */
    @Column(name = "price_per_kg", nullable = false, precision = 18, scale = 4)
    private BigDecimal pricePerKg;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
