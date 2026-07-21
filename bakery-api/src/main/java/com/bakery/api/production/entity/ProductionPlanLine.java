package com.bakery.api.production.entity;

import java.math.BigDecimal;

import com.bakery.api.master.entity.Item;
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
 * Một dòng trong kế hoạch sản xuất — tương ứng với 1 sản phẩm.
 *
 * <p>{@code suggestedQty}: số lượng do hệ thống tính (không thay đổi sau khi tạo).
 * <p>{@code adjustedQty}: số lượng sau khi manager điều chỉnh. Nếu null → dùng suggestedQty.
 */
@Getter
@Setter
@Entity
@Table(
        name = "production_plan_line",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "item_id"}))
public class ProductionPlanLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** SIMPLE | FREE_GROUP | BATCH_FORMULA */
    @Column(name = "plan_type", nullable = false, length = 20)
    private String planType;

    /** Group mà item này thuộc về (null nếu SIMPLE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProductionGroup group;

    /** Tồn kho thực tế lúc tạo plan (qtyRemainingActual từ DailyReportLine). */
    @Column(name = "qty_remaining", precision = 10, scale = 2)
    private BigDecimal qtyRemaining;

    /** Số lượng gợi ý do hệ thống tính — không thay đổi sau khi tạo. */
    @Column(name = "suggested_qty")
    private Integer suggestedQty;

    /** Số lượng sau khi manager điều chỉnh. Null = dùng suggestedQty. */
    @Column(name = "adjusted_qty")
    private Integer adjustedQty;

    /** Gram/đơn vị — chỉ dùng cho BATCH_FORMULA để FE validate tổng gram. */
    @Column(name = "grams_per_unit", precision = 8, scale = 2)
    private BigDecimal gramsPerUnit;

    /**
     * Số lượng default mỗi cối — chỉ dùng cho BATCH_FORMULA.
     * FE dùng để tính adjustedQty = defaultQtyPerBatch × num_batches khi admin đổi số cối.
     */
    @Column(name = "default_qty_per_batch")
    private Integer defaultQtyPerBatch;

    /** Ghi chú rule đã khớp — dùng để hiển thị lý do gợi ý cho manager. */
    @Column(name = "rule_note", length = 500)
    private String ruleNote;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** Trả về số lượng hiệu lực: adjustedQty nếu có, ngược lại suggestedQty. */
    public int getEffectiveQty() {
        return adjustedQty != null ? adjustedQty : (suggestedQty != null ? suggestedQty : 0);
    }
}
