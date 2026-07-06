package com.bakery.common.entity;

import com.bakery.common.entity.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Báo cáo cuối ngày của shop — nguồn độc lập trong 3-way reconciliation.
 *
 * Nhân viên shop submit mỗi tối:
 *   - qty_leftover_theoretical: số bánh lý thuyết còn lại (= nhập từ bếp - bán POS)
 *   - qty_destroyed_actual: số bánh thực tế hủy (hư hỏng, hết hạn)
 *
 * Variance = qty_pos_sold + qty_destroyed_actual - qty_bep_delivered
 * (tính trong view v_reconciliation)
 */
@Entity
@Table(
    name = "daily_shop_report",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_daily_shop_report",
        columnNames = {"report_date", "branch_id", "item_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyShopReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    @Builder.Default
    private ItemType itemType = ItemType.PRODUCT;

    @Column(name = "qty_leftover_theoretical", nullable = false, precision = 10, scale = 4)
    private BigDecimal qtyLeftoverTheoretical;

    @Column(name = "qty_destroyed_actual", nullable = false, precision = 10, scale = 4)
    private BigDecimal qtyDestroyedActual;

    @Column(name = "submitted_by", nullable = false, length = 100)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
