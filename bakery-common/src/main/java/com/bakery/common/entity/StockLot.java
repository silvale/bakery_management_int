package com.bakery.common.entity;

import com.bakery.common.entity.enums.StockLotStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kiện hàng nhập kho — mỗi kiện có barcode riêng tự sinh.
 *
 * Barcode format: LOT-{yyyyMMdd}-{random6} VD: LOT-20260508-A3F9K2
 *
 * Dùng để:
 *   - Track giá từng lô (FIFO costing)
 *   - Track người nhận khi xuất kho
 *   - Cảnh báo hàng sắp hết hạn
 */
@Entity
@Table(name = "stock_lot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockLot extends BaseEntity {

    /** Barcode tự sinh: LOT-{yyyyMMdd}-{random6} */
    @Column(name = "barcode", nullable = false, unique = true, length = 50)
    private String barcode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Tổng gram/ml nhập vào lô này */
    @Column(name = "qty_in_base_unit", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyInBaseUnit;

    /**
     * Còn lại trong lô (giảm dần khi xuất).
     * = qty_in_base_unit - issued_qty
     */
    @Column(name = "qty_remaining", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyRemaining;

    /** Đơn giá VNĐ / purchase_unit (copy từ order_line) */
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** Giá quy đổi VNĐ / gram hoặc VNĐ / ml */
    @Column(name = "price_per_base_unit", nullable = false, precision = 18, scale = 6)
    private BigDecimal pricePerBaseUnit;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** Tên người / bộ phận nhận khi xuất. Bắt buộc khi xuất kho. */
    @Column(name = "issued_to", length = 200)
    private String issuedTo;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    /** Tổng đã xuất từ lô này */
    @Column(name = "issued_qty", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal issuedQty = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StockLotStatus status = StockLotStatus.AVAILABLE;

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------
    /** Cập nhật số lượng sau khi xuất */
    public void issue(BigDecimal qty, String issuedTo, LocalDate date) {
        this.issuedQty    = this.issuedQty.add(qty);
        this.qtyRemaining = this.qtyInBaseUnit.subtract(this.issuedQty);
        this.issuedTo     = issuedTo;
        this.issuedDate   = date;
        if (this.qtyRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = StockLotStatus.DEPLETED;
        }
    }

    /** Kiểm tra có thể xuất không */
    public boolean isAvailable() {
        return this.status == StockLotStatus.AVAILABLE
            && this.qtyRemaining.compareTo(BigDecimal.ZERO) > 0;
    }
}
