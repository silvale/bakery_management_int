package com.bakery.api.modules.masterdata.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import com.bakery.api.framework.BaseEntity;

/**
 * Quy đổi đơn vị mua hàng → base_unit.
 *
 * Ví dụ:
 *   1 BAO_25KG bột mì  = 25,000 gram (base_quantity = 25000)
 *   1 THUNG_1L sữa tươi = 1,000 ml   (base_quantity = 1000)
 *   1 QUA trứng gà      = 60 gram     (base_quantity = 60)
 */
@Entity
@Table(
    name = "unit_conversion",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_unit_conversion_ingredient_purchase",
        columnNames = {"ingredient_id", "purchase_unit"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitConversion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /** Mã đơn vị mua: BAO_25KG, THUNG_1L, QUA, KG, ... */
    @Column(name = "purchase_unit", nullable = false, length = 50)
    private String purchaseUnit;

    /** Nhãn hiển thị: "Bao 25kg", "Thùng 1L", "1 Quả" */
    @Column(name = "purchase_unit_label", nullable = false, length = 100)
    private String purchaseUnitLabel;

    /**
     * 1 purchase_unit = base_quantity (gram hoặc ml).
     * VD: 1 bao 25kg = 25000 gram → baseQuantity = 25000
     */
    @Column(name = "base_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal baseQuantity;

    /** Đơn vị mua mặc định cho nguyên liệu này */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
