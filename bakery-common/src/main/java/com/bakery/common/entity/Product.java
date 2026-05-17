package com.bakery.common.entity;

import com.bakery.common.entity.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thành phẩm bánh.
 *
 * product_type:
 *   STANDARD   → quản lý theo cái (PCS): bánh mì, su kem, bồ đào nha...
 *   SHEET_CAKE → quản lý theo kg:  bánh Lan và tương tự
 *
 * tolerance_rate: ngưỡng sai số đối chiếu (%).
 *   0.05 = 5%. Quan trọng với SHEET_CAKE vì cắt thủ công.
 *   STANDARD thường = 0 (khớp chính xác).
 */
@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    /** Mã SP từ hệ thống bán hàng. VD: SP022575 */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    /** PCS | KG */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /**
     * Ngưỡng sai số đối chiếu.
     * 0.0500 = 5%. Với SHEET_CAKE vì cắt thủ công không chính xác tuyệt đối.
     */
    @Column(name = "tolerance_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal toleranceRate = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Recipe> recipes = new ArrayList<>();
}
