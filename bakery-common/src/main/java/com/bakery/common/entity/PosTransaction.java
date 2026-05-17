package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dữ liệu bán hàng từ máy POS — aggregate theo ngày.
 * Source: BigProductBySaleByCat.xlsx
 *
 * Lưu ý: POS có thể xuất nhiều dòng cùng Mã SP trong 1 ngày
 * (VD: SP001272 xuất hiện 2 lần) → Batch phải SUM trước khi insert.
 *
 * Các cột quan trọng từ file POS:
 *   Mã hàng    → product.code
 *   SL bán     → qty_sold
 *   Doanh thu  → revenue (đã tính giảm giá hóa đơn)
 *   SL trả     → qty_returned
 */
@Entity
@Table(
    name = "pos_transaction",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pos_transaction",
        columnNames = {"branch_id", "product_id", "transaction_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /** Tổng SL bán (đã aggregate, đã trừ SL trả) */
    @Column(name = "qty_sold", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtySold;

    /** Giá bán đơn vị (VND). Tính từ: revenue / qty_sold */
    @Column(name = "unit_price", precision = 18, scale = 4)
    private BigDecimal unitPrice;

    /** Doanh thu thuần (từ POS - cột "Doanh thu thuần") */
    @Column(name = "revenue", precision = 18, scale = 4)
    private BigDecimal revenue;

    @Column(name = "source_file", length = 500)
    private String sourceFile;
}
