package com.bakery.common.entity;

import com.bakery.common.entity.enums.EntityStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lô accessories nhập kho — dùng cho Max Price Engine.
 *
 * Max Price Engine: khi tính giá vốn accessories,
 * lấy đơn giá CAO NHẤT trong các lô còn hàng tại kho.
 * (Khác FIFO của nguyên liệu — accessories không theo thứ tự nhập)
 *
 * unit_price: giá/đơn vị bán (sell_unit) sau quy đổi từ purchase_unit.
 * VD: mua 1 thùng 12 hộp giá 120,000 → unit_price = 10,000 đ/hộp.
 */
@Entity
@Table(name = "product_stock_lot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "import_date", nullable = false)
    private LocalDate importDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Số lượng nhập (tính theo sell_unit) */
    @Column(name = "qty_imported", nullable = false, precision = 12, scale = 4)
    private BigDecimal qtyImported;

    /** Còn lại sau các lần xuất */
    @Column(name = "qty_remaining", nullable = false, precision = 12, scale = 4)
    private BigDecimal qtyRemaining;

    /** Giá VNĐ / sell_unit */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "is_depleted", nullable = false)
    @Builder.Default
    private Boolean isDepleted = false;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_status", nullable = false)
    @Builder.Default
    private EntityStatus entityStatus = EntityStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";
}
