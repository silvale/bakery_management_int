package com.bakery.common.entity;

import com.bakery.common.entity.enums.BaseUnit;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Nguyên liệu thô.
 * base_unit: đơn vị cơ sở dùng trong công thức (GRAM | ML).
 * Nhập kho theo purchase_unit, quy đổi về base_unit qua UnitConversion.
 */
@Entity
@Table(name = "ingredient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false, length = 20)
    @Builder.Default
    private BaseUnit baseUnit = BaseUnit.GRAM;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "ingredient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UnitConversion> unitConversions = new ArrayList<>();

    @OneToMany(mappedBy = "ingredient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IngredientPrice> prices = new ArrayList<>();
}
