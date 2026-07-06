package com.bakery.common.entity;

import com.bakery.common.entity.enums.RecipeLineType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;

/**
 * Chi tiết công thức thành phẩm.
 *
 * Mỗi dòng là MỘT TRONG HAI (XOR — enforce bằng @PrePersist + DB CHECK constraint):
 *   A) ingredient_id  IS NOT NULL → nguyên liệu thô / bao bì trực tiếp
 *   B) semi_product_id IS NOT NULL → bán thành phẩm (phôi, nhân, trang trí có công thức)
 *
 * quantity_gram: KL gram dùng cho 1 đơn vị sản phẩm.
 *   Đơn vị GRAM cho nguyên liệu/bán thành phẩm.
 *   Đơn vị PCS (=1000 gram ảo) cho bao bì — để đồng nhất cột.
 *
 * Cost contribution:
 *   A) ingredient (GRAM): quantity_gram * price_per_kg / 1_000_000
 *   A) ingredient (PCS):  1 * price_per_kg  (price_per_kg lưu giá/cái)
 *   B) semi_product:      quantity_gram / 1000 * semi_product_cost.cost_per_kg
 *
 * line_type:
 *   PHOI       → phôi bánh (semi_product)
 *   NHAN_CHINH → nhân chính (semi_product hoặc ingredient)
 *   NHAN_PHU   → nhân phụ gia (semi_product hoặc ingredient)
 *   TRANG_TRI  → trang trí (semi_product như SỐT MẶN, DA BEO, hoặc ingredient trực tiếp)
 *   BAOBI      → bao bì đóng gói (ingredient, unit=PCS): hộp, túi, dĩa nĩa, nến...
 */
@Entity
@Table(name = "recipe_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeLine extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    // -------------------------------------------------------
    // XOR: đúng 1 trong 2 phải NOT NULL
    // -------------------------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semi_product_id")
    private SemiProduct semiProduct;

    /**
     * KL gram dùng cho 1 đơn vị sản phẩm.
     * Với SHEET_CAKE là gram/kg thành phẩm.
     */
    @Column(name = "quantity_gram", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantityGram;

    /** @see RecipeLineType */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "line_type", nullable = false, length = 20)
    private RecipeLineType lineType;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // -------------------------------------------------------
    // Validation: XOR constraint
    // -------------------------------------------------------
    @PrePersist
    @PreUpdate
    private void validateXor() {
        boolean hasIngredient   = ingredient  != null;
        boolean hasSemiProduct  = semiProduct != null;
        if (hasIngredient == hasSemiProduct) {
            throw new IllegalStateException(
                "RecipeLine phải có đúng 1 trong 2: ingredient HOẶC semi_product, không được cả hai hoặc không có cái nào."
            );
        }
    }
}
