package com.bakery.api.modules.sales.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ghi nhận từng lần thu tiền cho đơn khách.
 *
 * Mỗi lần khách thanh toán (cọc / một phần / đầy đủ)
 * → tạo 1 record → cộng vào CustomerOrder.paidAmount
 * → hệ thống tự tính lại payment_status.
 */
@Entity
@Table(name = "customer_order_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    /** CASH | BANK_TRANSFER */
    @Column(name = "payment_type", nullable = false, length = 20)
    @Builder.Default
    private String paymentType = "BANK_TRANSFER";

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
