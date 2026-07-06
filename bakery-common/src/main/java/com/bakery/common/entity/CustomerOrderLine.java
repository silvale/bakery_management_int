package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Từng loại bánh trong đơn khách đặt.
 *
 * recipe_id: chỉ có khi khách yêu cầu công thức tùy chỉnh (is_custom = TRUE).
 * NULL = dùng công thức tiêu chuẩn hiện hành của product.
 *
 * total_price: computed column trong DB (qty * unit_price).
 */
@Entity
@Table(name = "customer_order_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Custom recipe nếu khách yêu cầu đặc biệt */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** Computed column từ DB: qty * unit_price. insertable/updatable = false */
    @Column(name = "total_price", insertable = false, updatable = false, precision = 18, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Add-on topping / NL đặc thù — chỉ có ý nghĩa khi product.productType = SHEET_CAKE */
    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CustomerOrderLineAddon> addons = new ArrayList<>();
}
