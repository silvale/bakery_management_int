package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Add-on topping / NL đặc thù cho đơn SHEET_CAKE.
 *
 * Mỗi order_line của SHEET_CAKE có thể có nhiều addon:
 *   addon_type = 'INGREDIENT' → thêm NL thô (VD: 50g dâu tây)
 *   addon_type = 'ACCESSORY'  → thêm sản phẩm phụ kiện (product_type=ACCESSORY)
 *
 * Dùng khi thợ bánh cần định lượng NL ngoài BOM chuẩn — phục vụ FifoEngine
 * trừ kho khi đơn chuyển sang IN_PRODUCTION.
 */
@Entity
@Table(name = "customer_order_line_addon")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerOrderLineAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private CustomerOrderLine line;

    /** 'INGREDIENT' | 'ACCESSORY' */
    @Column(name = "addon_type", nullable = false, length = 20)
    private String addonType = "INGREDIENT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "g";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
