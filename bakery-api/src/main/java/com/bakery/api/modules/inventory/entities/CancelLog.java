package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.bakery.api.framework.BaseEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Log FIFO hủy bánh cuối ngày (~20h).
 *
 * Flow:
 *   Admin nhập tổng qty hủy theo master product
 *   → FIFO trừ lô cũ nhất (gần hết HSD nhất)
 *   → Nếu không đủ → WARNING + ghi log để admin kiểm tra
 *
 * cancel_status:
 *   OK      → trừ đủ, khớp với lô cũ nhất
 *   WARNING → tồn lô cũ không đủ (NV có thể bán/hủy nhầm lô)
 *   PARTIAL → trừ được một phần, phần còn lại không có lô
 */
@Entity
@Table(name = "cancel_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CancelLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "cancel_date", nullable = false)
    private LocalDate cancelDate;

    /** Tổng số NV báo hủy */
    @Column(name = "qty_reported", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyReported;

    /** Thực tế đã trừ được từ các lô */
    @Column(name = "qty_cancelled", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal qtyCancelled = BigDecimal.ZERO;

    /**
     * Computed: qty_reported - qty_cancelled
     * insertable/updatable = false
     */
    @Column(name = "qty_discrepancy", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal qtyDiscrepancy;

    /**
     * Tổng cost hủy theo giá lô thực tế (Option B).
     * = SUM(cancel_log_detail.cancelled_cost)
     */
    @Column(name = "cancelled_cost", nullable = false, precision = 18, scale = 4)
    @Builder.Default
    private BigDecimal cancelledCost = BigDecimal.ZERO;

    /** OK | WARNING | PARTIAL */
    @Column(name = "cancel_status", nullable = false, length = 20)
    @Builder.Default
    private String cancelStatus = "OK";

    @Column(name = "warning_note", columnDefinition = "TEXT")
    private String warningNote;

    @OneToMany(mappedBy = "cancelLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CancelLogDetail> details = new ArrayList<>();

    // ── Helpers ───────────────────────────────────────────────

    public boolean hasWarning() {
        return "WARNING".equals(cancelStatus) || "PARTIAL".equals(cancelStatus);
    }
}
