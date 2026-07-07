package com.bakery.api.modules.inventory.entities;

import com.bakery.api.framework.enums.PaymentStatus;
import com.bakery.api.framework.enums.TransactionReason;
import com.bakery.api.framework.enums.TransactionStatus;
import com.bakery.api.framework.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.bakery.api.framework.BaseAdminEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.partner.entities.Supplier;

/**
 * Phiếu kho — bảng lõi Single-Table Ledger Architecture.
 * Extends BaseAdminEntity để tương thích với AdminBaseResource framework.
 *
 * entity_status = ACTIVE (default, không dùng để soft-delete phiếu)
 * status        = vòng đời phiếu: PENDING → READY → ACTIVE | REJECTED
 *
 * Luồng TRANSFER (2 bước):
 *   Cường approve → PENDING → READY (hiện tab PENDING bên KHO_BEP/SHOP)
 *   KHO_BEP/SHOP confirm → READY → ACTIVE (deduct from_branch, add to_branch)
 *
 * Luồng IMPORT / ADJUSTMENT (1 bước):
 *   Approve → PENDING → ACTIVE (tạo Inventory lot hoặc deduct/adjust)
 */
@Entity
@Table(
    name = "inventory_transaction",
    uniqueConstraints = @UniqueConstraint(name = "uq_inv_tx_code", columnNames = "code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction extends BaseAdminEntity {

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_reason", nullable = false, length = 30)
    private TransactionReason transactionReason;

    /**
     * VARCHAR tại DB — Java enum enforce valid values.
     * Tách biệt với entity_status (kế thừa từ BaseAdminEntity).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    /** Ngày nghiệp vụ — dùng cho reconciliation, tách biệt với created_at. */
    @Column(name = "transaction_date", nullable = false)
    @Builder.Default
    private LocalDate transactionDate = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id")
    private Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id")
    private Branch toBranch;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Relationships ────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryTransactionLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryTransactionPayment> payments = new ArrayList<>();
}
