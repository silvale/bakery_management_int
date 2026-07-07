package com.bakery.api.modules.sales.entities;

import com.bakery.api.framework.enums.EntityStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Đơn đặt hàng từ khách — bánh tươi đặt trước theo ngày giao.
 *
 * Code: ORD-{yyyyMMdd}-{seq}  VD: ORD-20260701-001
 *
 * Quy trình:
 *   PENDING → nhân viên confirm → CONFIRMED
 *   → tạo ProductionRequest(CUSTOMER_ORDER) → IN_PRODUCTION
 *   → bếp làm xong → READY
 *   → giao hàng → DELIVERED
 *
 * Thanh toán (payment_status):
 *   UNPAID → DEPOSIT → PARTIAL → PAID
 *   deposit_amount + paid_amount theo dõi thực nhận
 *   Mỗi lần thu tiền → tạo 1 CustomerOrderPayment
 */
@Entity
@Table(name = "customer_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** PENDING | CONFIRMED | IN_PRODUCTION | READY | DELIVERED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** UNPAID | DEPOSIT | PARTIAL | PAID */
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    /** Tổng tiền đơn hàng (computed từ lines) */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal depositAmount = BigDecimal.ZERO;

    /** Tổng số tiền đã thu (deposit + các lần thanh toán) */
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "entity_status", nullable = false)
    @Builder.Default
    private EntityStatus entityStatus = EntityStatus.ACTIVE;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CustomerOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CustomerOrderPayment> payments = new ArrayList<>();

    public BigDecimal getDebtAmount() {
        if (totalAmount == null) return BigDecimal.ZERO;
        return totalAmount.subtract(paidAmount);
    }
}
