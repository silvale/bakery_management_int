package com.bakery.common.entity;

import com.bakery.common.entity.enums.ReconcileStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Điều chuyển hàng từ Bếp → Cửa hàng (Tầng 2).
 *
 * qty_sent     → từ XuatRa.xlsx (bếp báo xuất)
 * qty_received → từ BaoCaoNgay.xlsx (cửa hàng nhận)
 *
 * qty_discrepancy = qty_sent - qty_received
 *   Dương = thất thoát (bếp xuất nhiều hơn cửa hàng nhận)
 *   Âm    = nhận nhiều hơn gửi (bất thường)
 *
 * Lưu ý: DB có computed column qty_discrepancy.
 *         JPA dùng @Formula để ánh xạ.
 */
@Entity
@Table(
    name = "stock_transfer",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_stock_transfer",
        columnNames = {"from_branch_id", "to_branch_id", "product_id", "transfer_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer extends BaseEntity {

    /** Bếp / kho xuất hàng */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id", nullable = false)
    private Branch fromBranch;

    /** Cửa hàng nhận hàng */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_branch_id", nullable = false)
    private Branch toBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    /** Số lượng Bếp báo xuất (XuatRa.xlsx - cột thực tế) */
    @Column(name = "qty_sent", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtySent;

    /** Số lượng Cửa hàng báo nhận (BaoCaoNgay.xlsx - cột bánh sáng). NULL = chưa confirm */
    @Column(name = "qty_received", precision = 12, scale = 3)
    private BigDecimal qtyReceived;

    /**
     * Computed column từ DB: qty_sent - COALESCE(qty_received, qty_sent).
     * insertable/updatable = false → Hibernate không write, chỉ read.
     */
    @Column(name = "qty_discrepancy", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal qtyDiscrepancy;

    /** PCS | KG */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus status = ReconcileStatus.PENDING;
}
