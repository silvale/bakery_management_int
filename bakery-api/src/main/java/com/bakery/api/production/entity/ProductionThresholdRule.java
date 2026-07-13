package com.bakery.api.production.entity;

import java.math.BigDecimal;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.DayType;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 1;

    /** COUNT (số tuyệt đối) hoặc PERCENT (% so với target ngày). */
    @Column(name = "condition_type", nullable = false, length = 10)
    private String conditionType;

    /** Giá trị ngưỡng — nếu tồn kho còn lại < conditionValue thì rule được kích hoạt. */
    @Column(name = "condition_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal conditionValue;

    /** Số lượng cần sản xuất thêm khi rule khớp. */
    @Column(name = "produce_qty", nullable = false)
    private int produceQty;
}
