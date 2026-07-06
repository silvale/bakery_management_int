package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only entity ánh xạ tới VIEW v_reconciliation (không phải bảng).
 *
 * Công thức 3-way reconciliation:
 *   variance = (qty_pos_sold + qty_destroyed) - qty_bep_delivered
 *
 * variance > 0  → Shop "tiêu thụ" nhiều hơn Bếp giao (thiếu hàng / sai số POS)
 * variance < 0  → Bếp giao nhiều hơn Shop ghi nhận (tồn chưa xử lý / mất mát)
 * variance = 0  → OK
 */
@Entity
@Immutable
@Subselect("""
    SELECT
        COALESCE(bep.item_id, pos.item_id, rpt.item_id)                             AS item_id,
        COALESCE(bep.transaction_date, pos.sales_date, rpt.report_date)              AS recon_date,
        COALESCE(bep.to_branch_id, pos.branch_id, rpt.branch_id)                    AS branch_id,
        COALESCE(bep.qty_bep_delivered, 0)                                           AS qty_bep_delivered,
        COALESCE(pos.qty_pos_sold,      0)                                           AS qty_pos_sold,
        COALESCE(rpt.qty_destroyed,     0)                                           AS qty_destroyed,
        (COALESCE(pos.qty_pos_sold, 0) + COALESCE(rpt.qty_destroyed, 0))
            - COALESCE(bep.qty_bep_delivered, 0)                                     AS variance
    FROM (
        SELECT t.transaction_date, t.to_branch_id, l.item_id,
               SUM(l.qty_approved) AS qty_bep_delivered
        FROM inventory_transaction t
        JOIN inventory_transaction_line l ON l.transaction_id = t.id
        WHERE t.transaction_type   = 'TRANSFER'
          AND t.transaction_reason = 'RESTOCK'
          AND t.status             = 'ACTIVE'
        GROUP BY t.transaction_date, t.to_branch_id, l.item_id
    ) bep
    FULL OUTER JOIN (
        SELECT sales_date, branch_id, item_id, SUM(qty_sold_pos) AS qty_pos_sold
        FROM pos_sales_data
        GROUP BY sales_date, branch_id, item_id
    ) pos ON  pos.item_id    = bep.item_id
          AND pos.sales_date = bep.transaction_date
          AND pos.branch_id  = bep.to_branch_id
    FULL OUTER JOIN (
        SELECT report_date, branch_id, item_id, SUM(qty_destroyed_actual) AS qty_destroyed
        FROM daily_shop_report
        GROUP BY report_date, branch_id, item_id
    ) rpt ON  rpt.item_id     = COALESCE(bep.item_id, pos.item_id)
          AND rpt.report_date = COALESCE(bep.transaction_date, pos.sales_date)
          AND rpt.branch_id   = COALESCE(bep.to_branch_id, pos.branch_id)
    """)
@IdClass(ReconciliationViewId.class)
@Getter
public class ReconciliationViewEntry {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Id
    @Column(name = "recon_date")
    private LocalDate reconDate;

    @Id
    @Column(name = "branch_id")
    private UUID branchId;

    /** Bếp giao sang Shop (TRANSFER ACTIVE) */
    @Column(name = "qty_bep_delivered", precision = 10, scale = 4)
    private BigDecimal qtyBepDelivered;

    /** POS ghi nhận doanh số */
    @Column(name = "qty_pos_sold", precision = 10, scale = 4)
    private BigDecimal qtyPosSold;

    /** Shop báo cáo hủy */
    @Column(name = "qty_destroyed", precision = 10, scale = 4)
    private BigDecimal qtyDestroyed;

    /**
     * variance = (qty_pos_sold + qty_destroyed) - qty_bep_delivered
     * Tính sẵn trong view, dương = thiếu hàng, âm = thừa / mất mát
     */
    @Column(name = "variance", precision = 10, scale = 4)
    private BigDecimal variance;
}
