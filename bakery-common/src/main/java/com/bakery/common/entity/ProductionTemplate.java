package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "production_template")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionTemplate extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /**
     * Số lượng mục tiêu T2-T6 (Weekday).
     * qty_to_produce = MAX(0, weekday_qty - qty_closing)
     */
    @Column(name = "weekday_qty", precision = 12, scale = 3)
    private BigDecimal weekdayQty;

    /**
     * Số lượng mục tiêu T7-CN (Weekend). Thường cao hơn weekday.
     * qty_to_produce = MAX(0, weekend_qty - qty_closing)
     */
    @Column(name = "weekend_qty", precision = 12, scale = 3)
    private BigDecimal weekendQty;

    /**
     * Legacy — giữ để backward compatible.
     * Dùng weekday/weekend_qty thay thế.
     */
    @Column(name = "default_qty", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal defaultQty = BigDecimal.ZERO;

    /**
     * Lấy số lượng mục tiêu theo ngày trong tuần.
     * T7, CN → weekend_qty; còn lại → weekday_qty
     * Fallback về default_qty nếu chưa có weekday/weekend.
     */
    public BigDecimal getQtyForDay(java.time.DayOfWeek day) {
        boolean isWeekend = (day == java.time.DayOfWeek.SATURDAY
                          || day == java.time.DayOfWeek.SUNDAY);
        if (isWeekend && weekendQty != null) return weekendQty;
        if (!isWeekend && weekdayQty != null) return weekdayQty;
        return defaultQty;
    }

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
