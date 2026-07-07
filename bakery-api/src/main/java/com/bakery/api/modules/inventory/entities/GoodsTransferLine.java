package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Từng mục NL trong GoodsTransfer.
 *
 * qty_from_recipe — lượng công thức cần (trước whole-unit rounding)
 * qty_requested   — lượng thực xuất (sau rounding, ví dụ cục bơ 5kg dù chỉ cần 4kg)
 * avg_unit_price  — giá bình quân FEFO, tính khi COMPLETED
 */
@Entity
@Table(name = "goods_transfer_line")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GoodsTransferLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private GoodsTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "unit", nullable = false, length = 20)
    @Builder.Default
    private String unit = "GRAM";

    /**
     * Lượng công thức yêu cầu (trước rounding).
     * NULL nếu tạo thủ công (không từ plan).
     * VD: công thức cần 4,000g bơ
     */
    @Column(name = "qty_from_recipe", precision = 18, scale = 4)
    private BigDecimal qtyFromRecipe;

    /**
     * Lượng thực xuất (sau whole-unit rounding).
     * VD: bơ is_whole_unit_only=true, packaging=5000g → qty_requested=5000g
     */
    @Column(name = "qty_requested", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyRequested;

    /** Giá bình quân FEFO — tính khi COMPLETED */
    @Column(name = "avg_unit_price", precision = 18, scale = 6)
    private BigDecimal avgUnitPrice;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
