package com.bakery.api.production.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Số lượng kế hoạch cho 1 nhóm sản xuất trong 1 production plan.
 *
 * <ul>
 *   <li><b>FREE_GROUP</b>: {@code plannedQty} = tổng sản phẩm mục tiêu
 *       (đã resolve weekday vs weekend tại thời điểm tạo plan).</li>
 *   <li><b>BATCH_FORMULA</b>: {@code plannedQty} = số cối (num_batches),
 *       do admin nhập trên màn hình kế hoạch.</li>
 * </ul>
 *
 * <p>expandBom() đọc {@code plannedQty} từ đây thay vì tính ngược từ plan lines.
 */
@Getter
@Setter
@Entity
@Table(
        name = "production_plan_group",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "group_id"}))
public class ProductionPlanGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductionGroup group;

    /**
     * FREE_GROUP: tổng sản phẩm mục tiêu.
     * BATCH_FORMULA: số cối (num_batches).
     */
    @Column(name = "planned_qty", nullable = false)
    private int plannedQty = 1;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
