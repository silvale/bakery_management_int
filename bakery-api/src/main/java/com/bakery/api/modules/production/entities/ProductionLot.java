package com.bakery.api.modules.production.entities;

import com.bakery.api.framework.enums.LotCostStatus;
import com.bakery.api.framework.enums.LotStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.bakery.api.framework.BaseEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Lô sản xuất bánh — 1 barcode/lô.
 *
 * Barcode format: LOT-{yyyyMMdd}-{SP_CODE}-{seq}
 * VD: LOT-20260508-SP022575-001
 *
 * Hỗ trợ Parent-Child cho bánh Lan:
 *   Parent: Lan khổ lớn (chưa cắt) — parent_lot_id = NULL
 *   Child:  Lan đã cắt — parent_lot_id trỏ về khổ lớn
 */
@Entity
@Table(name = "production_lot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionLot extends BaseEntity {

    /** Barcode: LOT-{yyyyMMdd}-{SP_CODE}-{seq} */
    @Column(name = "lot_number", nullable = false, unique = true, length = 60)
    private String lotNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    // ── Số lượng ─────────────────────────────────────────────

    @Column(name = "qty_produced", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyProduced;

    @Column(name = "qty_sold", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtySold = BigDecimal.ZERO;

    @Column(name = "qty_cancelled", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyCancelled = BigDecimal.ZERO;

    /**
     * Computed column từ DB: qty_produced - qty_sold - qty_cancelled
     * insertable/updatable = false
     */
    @Column(name = "qty_remaining", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal qtyRemaining;

    // ── Cost FIFO ─────────────────────────────────────────────

    /**
     * Giá vốn/cái. Chốt ngay lúc sản xuất theo FIFO.
     * Immutable sau khi costStatus = CONFIRMED.
     */
    @Column(name = "cost_per_unit", nullable = false, precision = 18, scale = 6)
    @Builder.Default
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "cost_status", nullable = false, length = 20)
    @Builder.Default
    private LotCostStatus costStatus = LotCostStatus.CONFIRMED;

    // ── Bánh Lan: Parent-Child ────────────────────────────────

    /** NULL = bánh thường hoặc Lan khổ lớn. NOT NULL = Lan đã cắt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_lot_id")
    private ProductionLot parentLot;

    /** Các lô con (Lan đã cắt từ khổ này) */
    @OneToMany(mappedBy = "parentLot", fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductionLot> childLots = new ArrayList<>();

    /** Trọng lượng thực tế (dùng cho Lan, tính cost theo tỷ lệ kg) */
    @Column(name = "weight_kg", precision = 10, scale = 4)
    private BigDecimal weightKg;

    // ── Liên kết ─────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LotStatus status = LotStatus.ACTIVE;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // ── Helpers ───────────────────────────────────────────────

    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate);
    }

    public boolean isExpiringSoon(int withinDays) {
        return !isExpired() &&
            !LocalDate.now().plusDays(withinDays).isBefore(expiryDate);
    }

    public boolean isPendingCost() {
        return this.costStatus == LotCostStatus.PENDING;
    }

    /** Tính cost cho Lan cắt từ khổ lớn: (weight_kg / parent.weightKg) * parent.costPerUnit * parent.qtyProduced */
    public BigDecimal calculateChildCost() {
        if (parentLot == null || weightKg == null) return BigDecimal.ZERO;
        if (parentLot.getWeightKg() == null || parentLot.getWeightKg().compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;
        BigDecimal totalParentCost = parentLot.getCostPerUnit()
            .multiply(parentLot.getQtyProduced());
        return totalParentCost.multiply(weightKg)
            .divide(parentLot.getWeightKg(), 6, java.math.RoundingMode.HALF_UP);
    }
}
