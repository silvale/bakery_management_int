package com.bakery.api.modules.masterdata.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.bakery.api.framework.BaseAdminEntity;

/**
 * Giá bán sản phẩm theo version.
 *
 * Mỗi lần thay đổi giá → tạo version mới qua AdminBase approval workflow.
 * Giá mới chỉ có hiệu lực từ effective_date sau khi được approve.
 *
 * Lookup giá tại ngày X:
 *   SELECT * FROM product_price
 *   WHERE product_id = ? AND effective_date <= X
 *   ORDER BY effective_date DESC LIMIT 1
 *
 * Đơn vị giá:
 *   STANDARD  → VNĐ/cái  (vd: 15,000 VNĐ/cái)
 *   SHEET_CAKE → VNĐ/kg  (vd: 320,000 VNĐ/kg)
 *
 * Dùng để:
 *   - Kiểm tra doanh thu: expected_revenue = qty_sold × price vs actual POS
 *   - Tính lợi nhuận:     profit = (price - cost_per_unit) × qty_sold
 *   - Phân tích break-even khi nguyên liệu tăng giá
 */
@Entity
@Table(
    name = "product_price",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_product_price_version",
        columnNames = {"product_id", "version"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPrice extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Giá bán VNĐ/unit.
     * STANDARD   → VNĐ/cái
     * SHEET_CAKE → VNĐ/kg
     */
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    /** Auto-increment per product. Version 1 = giá khởi tạo. */
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Ngày bắt đầu áp dụng giá này. Phải được approve trước ngày này. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
