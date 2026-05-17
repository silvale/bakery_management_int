package com.bakery.common.entity;

import com.bakery.common.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn nhập hàng từ nhà cung cấp.
 *
 * Code tự sinh: PO-{yyyyMMdd}-{seq}. VD: PO-20260508-001
 *
 * Công nợ = total_amount - paid_amount
 */
@Entity
@Table(name = "purchase_order")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder extends BaseEntity {

    /** Mã đơn tự sinh: PO-{yyyyMMdd}-{seq} */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    /** Ngày thực tế nhận hàng */
    @Column(name = "received_date")
    private LocalDate receivedDate;

    /** Tổng tiền đơn hàng */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** Số tiền đã thanh toán */
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    // -------------------------------------------------------
    // Helper
    // -------------------------------------------------------
    /** Công nợ còn lại */
    @Transient
    public BigDecimal getDebtAmount() {
        if (totalAmount == null) return BigDecimal.ZERO;
        return totalAmount.subtract(paidAmount);
    }
}
