package com.bakery.api.modules.sales.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.bakery.api.framework.BaseEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Kiểm kê cuối ngày tại cửa hàng.
 * Source: BaoCaoNgay.xlsx — sheet SX, Lan.
 *
 * qty_sold_derived (tính on-the-fly, KHÔNG lưu DB):
 *   = qty_opening + qty_received - qty_cancelled - qty_closing
 *
 * qty_cancelled: bánh huỷ (hết hạn, hỏng...) — field quan trọng!
 *   → tốn nguyên liệu nhưng không tạo doanh thu
 *   → bị trừ vào gross_profit trong DailyReconcile
 */
@Entity
@Table(
    name = "daily_inventory",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_inventory",
        columnNames = {"branch_id", "product_id", "inventory_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyInventory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "inventory_date", nullable = false)
    private LocalDate inventoryDate;

    /** Tồn đầu ngày (= qty_closing của ngày hôm qua) */
    @Column(name = "qty_opening", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyOpening = BigDecimal.ZERO;

    /** Nhận từ bếp hôm nay (BaoCaoNgay - cột "bánh sáng") */
    @Column(name = "qty_received", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    /** Bánh huỷ trong ngày (BaoCaoNgay - cột "hủy") */
    @Column(name = "qty_cancelled", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyCancelled = BigDecimal.ZERO;

    /** Kiểm kê còn lại cuối ngày (BaoCaoNgay - cột "tồn tối") */
    @Column(name = "qty_closing", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyClosing = BigDecimal.ZERO;

    /** Tổng bán do nhân viên ghi (BaoCaoNgay - cột H "tổng bán") — dùng để đối chiếu với POS */
    @Column(name = "qty_sold_reported", precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtySoldReported = BigDecimal.ZERO;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    // -------------------------------------------------------
    // Helper: tính qty_sold_derived on-the-fly
    // -------------------------------------------------------
    @Transient
    public BigDecimal getQtySoldDerived() {
        return qtyOpening
            .add(qtyReceived)
            .subtract(qtyCancelled)
            .subtract(qtyClosing);
    }
}
