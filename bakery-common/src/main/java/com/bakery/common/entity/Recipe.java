package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.List;

/**
 * Công thức thành phẩm (versioning theo effective_date).
 *
 * Versioning rule:
 *   - Mỗi product chỉ có 1 recipe is_active = TRUE tại 1 thời điểm.
 *   - Khi tạo version mới: set is_active = FALSE trên version cũ.
 *
 * Lookup công thức tại ngày X (dùng khi re-run báo cáo):
 *   SELECT * FROM recipe
 *   WHERE product_id = ? AND effective_date <= X
 *   ORDER BY effective_date DESC LIMIT 1
 *
 * DAILY_RECONCILE.recipe_id snapshot version đã dùng
 * → re-run luôn dùng đúng version cũ, không bị ảnh hưởng version mới.
 */
@Entity
@Table(
    name = "recipe",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_recipe_product_version",
        columnNames = {"product_id", "version"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "version", nullable = false)
    private Integer version;

    /** Chỉ 1 recipe active tại mỗi thời điểm cho mỗi product */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * TRUE nếu đây là công thức tùy chỉnh cho 1 đơn khách hàng cụ thể.
     * Custom recipe được clone từ recipe gốc, không thay đổi recipe gốc.
     */
    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private Boolean isCustom = false;

    /** Trỏ về recipe gốc được clone ra (chỉ có khi is_custom = TRUE) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_recipe_id")
    private Recipe parentRecipe;

    /** Lý do tùy chỉnh — ghi rõ khách yêu cầu gì khác (chỉ khi is_custom = TRUE) */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /**
     * BASE: công thức gốc (phôi + nhân chính).
     * ADDON: trang trí/phụ gia riêng cho 1 SKU.
     */
    @Column(name = "recipe_type", nullable = false, length = 10)
    @Builder.Default
    private String recipeType = "BASE";

    /**
     * NULL nếu đây là BASE recipe.
     * Trỏ về BASE recipe nếu đây là ADDON recipe.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_recipe_id")
    private Recipe baseRecipe;

    /** Các ADDON recipes tham chiếu về BASE recipe này */
    @OneToMany(mappedBy = "baseRecipe", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Recipe> addonRecipes = new ArrayList<>();

    public boolean isBase()  { return "BASE".equals(recipeType); }
    public boolean isAddon() { return "ADDON".equals(recipeType); }

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RecipeLine> lines = new ArrayList<>();
}
