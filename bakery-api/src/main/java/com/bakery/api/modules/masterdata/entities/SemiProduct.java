package com.bakery.api.modules.masterdata.entities;

import com.bakery.api.framework.enums.SemiProductType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.bakery.api.framework.BaseAdminEntity;

/**
 * Bán thành phẩm: Phôi và Nhân.
 *
 * type = PHOI : phôi bánh (Bột Viên Trắng, Đan Mạch, Su, Donut...)
 * type = NHAN : nhân bánh (Nhân Xá Xíu, Su Kem, Cade, Phô Mai...)
 *
 * total_yield_kg: KG thành phẩm làm ra từ 1 mẻ nguyên liệu.
 *   VD: Bột Viên Trắng → 14.201 kg phôi/mẻ
 *       Nhân Xá Xíu   → 1.054 kg nhân/mẻ
 *
 * cost_per_kg tính on-the-fly qua CostCalculationService (semi_product_cost đã bị xóa V15).
 */
@Entity
@Table(name = "semi_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemiProduct extends BaseAdminEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, length = 20)
    private SemiProductType type;

    /**
     * KG thành phẩm làm ra từ 1 mẻ.
     * Dùng để tính: cost_per_kg = total_cost_batch / total_yield_kg
     */
    @Column(name = "total_yield_kg", nullable = false, precision = 12, scale = 4)
    private BigDecimal totalYieldKg;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "semiProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeLineSemi> recipeLines = new ArrayList<>();

}
