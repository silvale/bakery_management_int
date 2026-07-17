package com.bakery.api.master.entity;

import com.bakery.api.production.entity.ItemGroup;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Base entity cho mọi loại vật tư/hàng hóa trong hệ thống.
 * Dùng Single Table Inheritance — toàn bộ Ingredient, SemiProduct, Product
 * đều được lưu trên bảng "item", phân biệt bằng cột item_type.
 *
 * <p>Foreign key từ các bảng khác (recipe_line, stock_lot, ...)
 * chỉ cần trỏ về item.id — đảm bảo DB integrity hoàn toàn.
 */
@Getter
@Setter
@Entity
@Table(name = "item")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "item_type", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class Item extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    /**
     * Phòng/bộ phận sản xuất: PL=Phòng Lạnh, PK=Phòng Kem, BMN=Bánh Mì Ngọt...
     * Dùng cho cả phân loại sản phẩm (thay thế productCategory) và filter kế hoạch SX.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_group_id")
    private ItemGroup itemGroup;

    /**
     * Kích thước đơn vị tối thiểu không thể tách lẻ.
     * Chỉ có ý nghĩa khi isSplittable = false.
     * Ví dụ: bơ 5kg/cục → unitSize = 5.0
     */
    @Column(name = "unit_size", precision = 15, scale = 4)
    private java.math.BigDecimal unitSize;

    /**
     * true (default) = có thể xuất lẻ từ kho tổng.
     * false = phải xuất theo bội số của unitSize.
     */
    @Column(name = "is_splittable", nullable = false)
    private boolean splittable = true;

    /**
     * Giá vốn per unit (đơn vị tính của item).
     * <ul>
     *   <li>INGREDIENT: nhập tay trực tiếp trên form.</li>
     *   <li>SEMI_PRODUCT / PRODUCT: tính tự động từ công thức khi approve recipe.</li>
     * </ul>
     */
    @Column(name = "unit_cost", precision = 15, scale = 4)
    private java.math.BigDecimal unitCost;
}
