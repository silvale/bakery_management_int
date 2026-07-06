package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "production_template")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionTemplate extends BaseAdminEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /** Số lượng mục tiêu T2-T6. */
    @Column(name = "weekday_qty", precision = 12, scale = 3)
    private BigDecimal weekdayQty;

    /** Số lượng mục tiêu T7-CN. Thường cao hơn weekday. */
    @Column(name = "weekend_qty", precision = 12, scale = 3)
    private BigDecimal weekendQty;

    /** Legacy fallback. Bằng weekday_qty. */
    @Column(name = "default_qty", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal defaultQty = BigDecimal.ZERO;

    /**
     * Loại rule tính số lượng sản xuất:
     * <ul>
     *   <li>SUBTRACT   — qty = MAX(0, target - tồn)  [mặc định]</li>
     *   <li>HALF       — qty = target nếu tồn &lt; target×0.5, else 0</li>
     *   <li>THRESHOLD  — qty = config_qty nếu tồn {op} threshold, else 0</li>
     *   <li>TIER       — multi-tier lookup từ rule_config JSON</li>
     *   <li>ADDITIVE   — qty = base + add nếu tồn &lt; threshold (Bento, Bánh bắp miếng)</li>
     *   <li>GROUP_COI  — logic theo cối, tính nhóm (Bánh bắp S12/S14)</li>
     * </ul>
     */
    @Column(name = "production_rule", nullable = false, length = 20)
    @Builder.Default
    private String productionRule = "SUBTRACT";

    /**
     * Tham số bổ sung cho rule — lưu dạng JSON text.
     * THRESHOLD: {"weekday":{"op":"lte","threshold":12,"qty":25},"weekend":{...}}
     * TIER:      {"weekday":[{"lte":6,"qty":10},{"qty":0}],"weekend":[...]}
     * ADDITIVE:  {"tiers":[{"lt":10,"add":24},{"lt":15,"add":12}]}
     */
    @Column(name = "rule_config", columnDefinition = "JSONB")
    private String ruleConfig;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Lấy target qty theo ngày trong tuần (trước khi áp rule). */
    public BigDecimal getQtyForDay(java.time.DayOfWeek day) {
        boolean isWeekend = (day == java.time.DayOfWeek.SATURDAY
                          || day == java.time.DayOfWeek.SUNDAY);
        if (isWeekend && weekendQty != null) return weekendQty;
        if (!isWeekend && weekdayQty != null) return weekdayQty;
        return defaultQty;
    }
}
