package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lô nguyên liệu nhập kho — dùng cho FIFO engine.
 *
 * Mỗi lần nhập kho → 1 record.
 * FIFO engine trừ qty_remaining của lô cũ nhất (import_date ASC) trước.
 *
 * Phân biệt với StockLot (V6):
 *   StockLot           = track vật lý kiện hàng + barcode kho
 *   IngredientStockLot = track giá theo lô cho FIFO cost engine
 */
@Entity
@Table(name = "ingredient_stock_lot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IngredientStockLot extends BaseAdminEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    /** Ngày nhập kho thực tế (hỗ trợ backdate) */
    @Column(name = "import_date", nullable = false)
    private LocalDate importDate;

    /** Hạn sử dụng — dùng cho FEFO (First Expired First Out). NULL nếu NL không có HSD */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Tổng nhập (gram/ml) */
    @Column(name = "qty_imported", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyImported;

    /** Còn lại sau FIFO trừ dần */
    @Column(name = "qty_remaining", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyRemaining;

    /** Giá VNĐ/gram hoặc VNĐ/ml = price_per_kg / 1,000,000 */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitPrice;

    /** TRUE khi qty_remaining = 0 — dùng để filter FIFO query */
    @Column(name = "is_depleted", nullable = false)
    @Builder.Default
    private Boolean isDepleted = false;

    /** TRUE nếu được nhập bù sau ngày thực tế */
    @Column(name = "is_backdate", nullable = false)
    @Builder.Default
    private Boolean isBackdate = false;

    /**
     * Phiếu chuyển kho sinh ra lô này.
     * Điền khi lô được tạo từ GoodsTransfer (approve).
     * Cho phép trace: lô KHO_BEP → phiếu TRF → lô KHO_TONG gốc.
     */
    @Column(name = "source_transfer_id")
    private UUID sourceTransferId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Trừ qty từ lô này (FIFO).
     * @return qty thực sự đã trừ được (có thể < qtyNeeded nếu không đủ)
     */
    public BigDecimal consume(BigDecimal qtyNeeded) {
        BigDecimal consumed = qtyRemaining.min(qtyNeeded);
        this.qtyRemaining = this.qtyRemaining.subtract(consumed);
        if (this.qtyRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            this.isDepleted = true;
            this.qtyRemaining = BigDecimal.ZERO;
        }
        return consumed;
    }
}
