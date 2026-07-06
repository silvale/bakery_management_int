package com.bakery.common.entity;

import com.bakery.common.entity.enums.BaseUnit;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
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
public class Ingredient extends BaseAdminEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "base_unit", nullable = false, length = 20, columnDefinition = "base_unit")
    @Builder.Default
    private BaseUnit baseUnit = BaseUnit.GRAM;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Đơn vị đóng gói khi nhập kho. VD: BLOCK, THUNG, BAO.
     * NULL nếu không có quy ước đóng gói đặc biệt.
     */
    @Column(name = "packaging_unit", length = 30)
    private String packagingUnit;

    /**
     * Số lượng base_unit trong 1 packaging_unit.
     * VD: bơ 1 block = 5,000 gram → packaging_qty = 5000.
     */
    @Column(name = "packaging_qty", precision = 18, scale = 4)
    private java.math.BigDecimal packagingQty;

    /**
     * TRUE = chỉ được xuất kho nguyên block/thùng, không xuất lẻ.
     * Dùng để validate khi tạo GoodsTransferLine.
     */
    @Column(name = "is_whole_unit_only", nullable = false)
    @Builder.Default
    private Boolean isWholeUnitOnly = false;

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
