package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Thanh toán nhiều đợt cho phiếu nhập hàng.
 * Chỉ áp dụng cho IMPORT (transaction_type = IMPORT).
 * Tổng các payment.amount không bắt buộc bằng total_amount — tracking công nợ từng đợt.
 */
@Entity
@Table(name = "inventory_transaction_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private InventoryTransaction transaction;

    @Column(name = "payment_date", nullable = false)
    @Builder.Default
    private OffsetDateTime paymentDate = OffsetDateTime.now();

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /**
     * CASH | BANK_TRANSFER — lưu plain string để linh hoạt mở rộng.
     */
    @Column(name = "payment_method", nullable = false, length = 20)
    @Builder.Default
    private String paymentMethod = "CASH";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
