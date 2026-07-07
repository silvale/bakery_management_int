package com.bakery.api.modules.inventory.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.bakery.api.framework.BaseEntity;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Phiếu xuất bánh từ Kho Bếp → Cửa Hàng.
 *
 * Quy trình:
 *   1. Bếp trưởng tạo phiếu (status = PENDING), nhập qty_sent.
 *   2. Nhân viên cửa hàng xác nhận nhận hàng (status = CONFIRMED), nhập qty_received.
 *   3. Nếu qty_received khác qty_sent → ghi chênh lệch, vẫn CONFIRMED.
 *   4. Nhân viên có thể REJECT nếu hàng chưa đến hoặc có vấn đề.
 *
 * qty_discrepancy = qty_sent - COALESCE(qty_received, qty_sent)
 *   Dương = thất thoát; Âm = nhận nhiều hơn gửi (bất thường).
 *   DB có computed column, JPA dùng insertable=false updatable=false.
 */
@Entity
@Table(
    name = "stock_transfer",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_stock_transfer",
        columnNames = {"from_branch_id", "to_branch_id", "product_id", "transfer_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer extends BaseEntity {

    /** Bếp / kho xuất hàng */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id", nullable = false)
    private Branch fromBranch;

    /** Cửa hàng nhận hàng */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_branch_id", nullable = false)
    private Branch toBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    /** Số lượng Bếp báo xuất (XuatRa.xlsx - cột thực tế) */
    @Column(name = "qty_sent", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtySent;

    /** Số lượng Cửa hàng báo nhận (BaoCaoNgay.xlsx - cột bánh sáng). NULL = chưa confirm */
    @Column(name = "qty_received", precision = 12, scale = 3)
    private BigDecimal qtyReceived;

    /**
     * Computed column từ DB: qty_sent - COALESCE(qty_received, qty_sent).
     * insertable/updatable = false → Hibernate không write, chỉ read.
     */
    @Column(name = "qty_discrepancy", insertable = false, updatable = false, precision = 12, scale = 3)
    private BigDecimal qtyDiscrepancy;

    /** PCS | KG */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    /**
     * PENDING   → bếp đã tạo, chờ cửa hàng xác nhận
     * CONFIRMED → cửa hàng đã xác nhận nhận hàng
     * REJECTED  → cửa hàng từ chối (hàng chưa đến / có vấn đề)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Username người xác nhận / từ chối */
    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
}
