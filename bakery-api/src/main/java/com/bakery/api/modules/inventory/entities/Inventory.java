package com.bakery.api.modules.inventory.entities;

import com.bakery.api.framework.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Branch;

/**
 * Tồn kho theo lô — bảng lõi của Single-Table Ledger Architecture.
 *
 * Mỗi row = 1 lô hàng tại 1 chi nhánh.
 * item_id là polymorphic FK: trỏ tới ingredient.id hoặc product.id tùy item_type.
 * FEFO index: idx_inv_fefo (branch_id, item_id, expiry_date ASC) WHERE qty_available > 0
 *
 * Rows với qty_available = 0 được GIỮ LẠI để audit trail (không xóa).
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /**
     * Polymorphic FK — trỏ tới ingredient.id hoặc product.id tùy item_type.
     * Không có DB-level FK vì hai bảng target khác nhau.
     */
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType;

    @Column(name = "qty_available", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal qtyAvailable = BigDecimal.ZERO;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "cost_per_unit", precision = 18, scale = 6)
    private BigDecimal costPerUnit;

    /**
     * Phiếu nhập kho đã tạo ra lô này.
     * Tham chiếu tới inventory_transaction.id (soft ref, không cascade).
     */
    @Column(name = "source_tx_id")
    private UUID sourceTxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
