package com.bakery.api.production.entity;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Dòng cấu hình sản lượng cho từng sản phẩm trong template.
 *
 * <p>Ví dụ: Bánh mì gối — WEEKEND
 *   qty_target = 100, trigger_threshold_percent = 50, batch_size = 12
 *   → Khi tồn kho < 50 cái thì kích hoạt làm thêm, mỗi lần làm đúng 12 cái.
 */
@Getter
@Setter
@Entity
@Table(name = "product_plan_template_line")
public class ProductPlanTemplateLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ProductPlanTemplate template;

    /** Sản phẩm cần lên kế hoạch (item_type = PRODUCT) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Sản lượng mục tiêu trong ngày */
    @Column(name = "qty_target", nullable = false)
    private Integer qtyTarget;

    /** % tồn kho tối thiểu để kích hoạt sản xuất thêm (0–100) */
    @Column(name = "trigger_threshold_percent", nullable = false)
    private Integer triggerThresholdPercent;

    /** Số lượng cố định mỗi mẻ sản xuất */
    @Column(name = "batch_size", nullable = false)
    private Integer batchSize;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
