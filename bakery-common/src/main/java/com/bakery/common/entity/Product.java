package com.bakery.common.entity;

import com.bakery.common.entity.enums.ProductType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
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
public class Product extends BaseAdminEntity {

    /** Mã SP từ hệ thống bán hàng. VD: SP022575 */
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
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

    // ── Accessories only ─────────────────────────────────────
    /** Đơn vị mua vào (VD: THUNG_12, HOP_6, KG). NULL với STANDARD/SHEET_CAKE */
    @Column(name = "purchase_unit", length = 50)
    private String purchaseUnit;

    /** Số đơn vị bán lẻ trong 1 đơn vị mua (VD: 12 cái/thùng). NULL với STANDARD/SHEET_CAKE */
    @Column(name = "units_per_purchase", precision = 12, scale = 4)
    private java.math.BigDecimal unitsPerPurchase;

    /** Đơn vị bán lẻ (VD: CAI, HOP, KG). NULL với STANDARD/SHEET_CAKE */
    @Column(name = "sell_unit", length = 50)
    private String sellUnit;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Recipe> recipes = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductPrice> prices = new ArrayList<>();
}
