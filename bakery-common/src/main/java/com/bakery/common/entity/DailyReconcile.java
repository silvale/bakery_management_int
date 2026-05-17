package com.bakery.common.entity;

import com.bakery.common.entity.enums.ReconcileStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tổng hợp đối chiếu 3 tầng + Cost + Lợi nhuận theo ngày.
 *
 * ─────────────────────────────────────────────
 *  TẦNG 1 — Sản xuất (BanhRaNgay vs XuatRa)
 *    qty_requested   → Admin yêu cầu
 *    qty_produced    → Bếp thực tế làm ra
 *    diff = qty_produced - qty_requested
 *    status: OK | OVER | UNDER
 *
 *  TẦNG 2 — Vận chuyển (XuatRa vs BaoCaoNgay)
 *    qty_sent        → Bếp xuất (XuatRa)
 *    qty_received    → Cửa hàng nhận (BaoCaoNgay - bánh sáng)
 *    diff = qty_sent - qty_received
 *    status: OK | DISCREPANCY
 *
 *  TẦNG 3 — Bán hàng (BaoCaoNgay vs POS)
 *    qty_sold_pos      → Máy POS ghi nhận
 *    qty_sold_derived  → Tính từ kiểm kê:
 *                        opening + received - cancelled - closing
 *    diff = qty_sold_pos - qty_sold_derived
 *    status: OK | DISCREPANCY
 *
 * ─────────────────────────────────────────────
 *  GROSS PROFIT:
 *    revenue        = qty_sold_pos * unit_price
 *    sales_cost     = qty_sold_pos * cost_per_unit
 *    cancelled_cost = qty_cancelled * cost_per_unit
 *    gross_profit   = revenue - sales_cost - cancelled_cost
 * ─────────────────────────────────────────────
 */
@Entity
@Table(
    name = "daily_reconcile",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_reconcile",
        columnNames = {"branch_id", "product_id", "recon_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyReconcile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "recon_date", nullable = false)
    private LocalDate reconDate;

    // -------------------------------------------------------
    // Tầng 1: Sản xuất
    // -------------------------------------------------------
    @Column(name = "qty_requested", precision = 12, scale = 3)
    private BigDecimal qtyRequested;

    @Column(name = "qty_produced", precision = 12, scale = 3)
    private BigDecimal qtyProduced;

    @Column(name = "production_vs_order_diff", precision = 12, scale = 3)
    private BigDecimal productionVsOrderDiff;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_vs_order_status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus productionVsOrderStatus = ReconcileStatus.PENDING;

    // -------------------------------------------------------
    // Tầng 2: Vận chuyển Bếp → Cửa hàng
    // -------------------------------------------------------
    @Column(name = "qty_sent", precision = 12, scale = 3)
    private BigDecimal qtySent;

    @Column(name = "qty_received", precision = 12, scale = 3)
    private BigDecimal qtyReceived;

    @Column(name = "delivery_vs_receipt_diff", precision = 12, scale = 3)
    private BigDecimal deliveryVsReceiptDiff;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_vs_receipt_status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus deliveryVsReceiptStatus = ReconcileStatus.PENDING;

    // -------------------------------------------------------
    // Tầng 3: Bán hàng
    // -------------------------------------------------------
    @Column(name = "qty_sold_pos", precision = 12, scale = 3)
    private BigDecimal qtySoldPos;

    @Column(name = "qty_opening", precision = 12, scale = 3)
    private BigDecimal qtyOpening;

    @Column(name = "qty_cancelled", precision = 12, scale = 3)
    private BigDecimal qtyCancelled;

    @Column(name = "qty_closing", precision = 12, scale = 3)
    private BigDecimal qtyClosing;

    /** Tổng bán nhân viên ghi (cột H - BaoCaoNgay) — so sánh với POS */
    @Column(name = "qty_sold_reported", precision = 12, scale = 3)
    private BigDecimal qtySoldReported;

    /** Tồn tối lý thuyết = (opening + received) - (sold_reported + cancelled)
     *  So sánh với qty_closing thực tế để validate data nhân viên nhập */
    @Column(name = "qty_sold_derived", precision = 12, scale = 3)
    private BigDecimal qtySoldDerived;

    @Column(name = "pos_vs_inventory_diff", precision = 12, scale = 3)
    private BigDecimal posVsInventoryDiff;

    @Enumerated(EnumType.STRING)
    @Column(name = "pos_vs_inventory_status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus posVsInventoryStatus = ReconcileStatus.PENDING;

    // -------------------------------------------------------
    // Snapshot công thức & giá (để re-run chính xác)
    // -------------------------------------------------------
    /** Snapshot recipe_id đã dùng → re-run không bị ảnh hưởng version mới */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    /** Snapshot version giá nguyên liệu đã dùng */
    @Column(name = "ingredient_price_version")
    private Integer ingredientPriceVersion;

    /** Snapshot version cost phôi/nhân đã dùng */
    @Column(name = "semi_product_cost_version")
    private Integer semiProductCostVersion;

    // -------------------------------------------------------
    // Cost & Lợi nhuận
    // -------------------------------------------------------
    /** Cost 1 đơn vị = SUM(phôi + nhân + trang trí) tại recon_date */
    @Column(name = "cost_per_unit", precision = 18, scale = 4)
    private BigDecimal costPerUnit;

    /** Giá bán (từ POS) */
    @Column(name = "unit_price", precision = 18, scale = 4)
    private BigDecimal unitPrice;

    /** qty_sold_pos * unit_price */
    @Column(name = "revenue", precision = 18, scale = 4)
    private BigDecimal revenue;

    /** qty_sold_pos * cost_per_unit */
    @Column(name = "sales_cost", precision = 18, scale = 4)
    private BigDecimal salesCost;

    /** qty_cancelled * cost_per_unit — chi phí nguyên liệu mất do huỷ */
    @Column(name = "cancelled_cost", precision = 18, scale = 4)
    private BigDecimal cancelledCost;

    /** revenue - sales_cost - cancelled_cost. Chưa tính công + mặt bằng */
    @Column(name = "gross_profit", precision = 18, scale = 4)
    private BigDecimal grossProfit;

    // -------------------------------------------------------
    // Trạng thái tổng
    // -------------------------------------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus overallStatus = ReconcileStatus.PENDING;

    @Column(name = "discrepancy_note", columnDefinition = "TEXT")
    private String discrepancyNote;
}
