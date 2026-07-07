package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.bakery.api.framework.BaseEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;

/**
 * Tồn kho nguyên liệu tổng hợp theo ingredient + branch.
 *
 * Cập nhật khi:
 *   + Nhập hàng mới    → qty_on_hand tăng
 *   - Xuất sản xuất    → qty_on_hand giảm
 *   ~ Kiểm kê điều chỉnh
 *
 * qty_available (computed) = qty_on_hand - qty_reserved
 */
@Entity
@Table(
    name = "ingredient_stock",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ingredient_stock",
        columnNames = {"ingredient_id", "branch_id"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IngredientStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Tồn thực tế (gram/ml). Tăng khi nhập, giảm khi xuất sản xuất. */
    @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    /** Đã reserve cho đơn sản xuất nhưng chưa xuất thực tế */
    @Column(name = "qty_reserved", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    /**
     * Computed column từ DB: qty_on_hand - qty_reserved
     * insertable/updatable = false
     */
    @Column(name = "qty_available", insertable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal qtyAvailable;

    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private OffsetDateTime lastUpdated = OffsetDateTime.now();

    /**
     * Ngày kiểm đếm phụ kiện gần nhất tại branch này.
     * NULL = chưa kiểm lần nào.
     * Dùng làm period_from khi tính POS sold cho đợt kiểm kế tiếp.
     */
    @Column(name = "last_reconcile_date")
    private LocalDate lastReconcileDate;

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------
    /** Nhập thêm hàng vào kho */
    public void addStock(BigDecimal qty) {
        this.qtyOnHand   = this.qtyOnHand.add(qty);
        this.lastUpdated = OffsetDateTime.now();
    }

    /** Xuất hàng khỏi kho */
    public void removeStock(BigDecimal qty) {
        this.qtyOnHand   = this.qtyOnHand.subtract(qty);
        this.lastUpdated = OffsetDateTime.now();
        if (this.qtyOnHand.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                "Tồn kho không đủ cho nguyên liệu: " + ingredient.getCode()
            );
        }
    }
}
