package com.bakery.api.production.entity;

import java.math.BigDecimal;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.DayType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Rule ngưỡng sản xuất — Pattern 1: SIMPLE.
 *
 * <p>Mỗi sản phẩm có thể có nhiều rule, sắp xếp theo {@code sortOrder}.
 * Hệ thống khớp rule đầu tiên (sort_order tăng dần) khi {@code qtyRemaining < conditionValue}.
 *
 * <p>Ví dụ: dưới 5 → làm thêm 24; dưới 12 → làm thêm 12.
 */
@Getter
@Setter
@Entity
@Table(
        name = "production_threshold_rule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "day_type", "sort_order"}))
public class ProductionThresholdRule extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    /**
     * Cách đo ngưỡng kích hoạt:
     * COUNT   — tồn < conditionValue (số tuyệt đối)
     * PERCENT — tồn < conditionValue% × actionValue
     */
    @Column(name = "condition_type", nullable = false, length = 10)
    private String conditionType;

    /** Giá trị ngưỡng. COUNT: số tuyệt đối. PERCENT: phần trăm (0–100). */
    @Column(name = "condition_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal conditionValue;

    /**
     * Hành động khi rule khớp:
     * PRODUCE_MORE   — làm thêm đúng actionValue cái
     * FILL_TO_TARGET — làm thêm max(0, actionValue − tồn) cái
     */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType = "PRODUCE_MORE";

    /**
     * Giá trị hành động — ý nghĩa tuỳ actionType:
     * PRODUCE_MORE   → số lượng cố định làm thêm
     * FILL_TO_TARGET → target cần đạt (làm đủ lên con số này)
     */
    @Column(name = "action_value", nullable = false)
    private int actionValue;
}
