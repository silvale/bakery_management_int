package com.bakery.api.modules.production.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Product;

/**
 * Từng loại bánh trong kế hoạch sản xuất ngày.
 *
 * qty_planned: hệ thống tính toán (từ template - tồn kho).
 * qty_adjusted: Chính điều chỉnh khi review (NULL = dùng qty_planned).
 *
 * Sau khi plan APPROVED → mỗi line tạo ra 1 ProductionRequest tương ứng.
 */
@Entity
@Table(name = "production_plan_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPlanLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private ProductionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Số lượng hệ thống đề xuất (template - tồn kho) */
    @Column(name = "qty_planned", nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyPlanned;

    /** Số lượng Chính điều chỉnh. NULL = dùng qty_planned */
    @Column(name = "qty_adjusted", precision = 12, scale = 3)
    private BigDecimal qtyAdjusted;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Số lượng thực sự cần làm = COALESCE(qty_adjusted, qty_planned) */
    public BigDecimal getEffectiveQty() {
        return qtyAdjusted != null ? qtyAdjusted : qtyPlanned;
    }
}
