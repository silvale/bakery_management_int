package com.bakery.api.master.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng quy đổi đơn vị — dùng trong RecipeCostService.
 *
 * <p>Ý nghĩa của {@code factor}:
 * <pre>
 *   cost_per_from_unit = item.unitCost × factor
 *   (vì item.unitCost tính theo to_unit)
 *
 *   Ví dụ: item.unit = KG, line.unit = G
 *   → from_unit=G, to_unit=KG, factor=0.001
 *   → giá 1g = unitCost(đ/kg) × 0.001
 * </pre>
 */
@Getter
@Setter
@Entity
@Table(name = "unit_conversion")
@IdClass(UnitConversionId.class)
public class UnitConversion {

    /** Đơn vị trong recipe line (đơn vị cần tính giá). */
    @Id
    @Column(name = "from_unit", nullable = false, length = 20)
    private String fromUnit;

    /** Đơn vị của item (đơn vị mà unit_cost được lưu theo). */
    @Id
    @Column(name = "to_unit", nullable = false, length = 20)
    private String toUnit;

    /**
     * Hệ số quy đổi: 1 from_unit = factor × to_unit.
     * cost_per_from_unit = unit_cost(per to_unit) × factor.
     */
    @Column(name = "factor", nullable = false, precision = 20, scale = 8)
    private BigDecimal factor;

    @Column(name = "note", length = 200)
    private String note;
}
