package com.bakery.api.production.entity;

import java.math.BigDecimal;

import com.bakery.api.master.entity.Item;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.ProductionLineStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "production_request_line")
public class ProductionRequestLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_request_id", nullable = false)
    private ProductionRequest productionRequest;

    /** Sản phẩm cần sản xuất (item_type = PRODUCT) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Item product;

    /**
     * Recipe version dùng để tính NL khi approve.
     * NULL = dùng recipe active mới nhất của product.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(name = "planned_qty", nullable = false, precision = 15, scale = 4)
    private BigDecimal plannedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_status", nullable = false, length = 20)
    private ProductionLineStatus lineStatus = ProductionLineStatus.PENDING;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "note", length = 500)
    private String note;

    /** 1-1 với DeliveryRecord, tạo khi bếp bấm Completed */
    @OneToOne(mappedBy = "productionRequestLine", fetch = FetchType.LAZY)
    private DeliveryRecord deliveryRecord;
}
