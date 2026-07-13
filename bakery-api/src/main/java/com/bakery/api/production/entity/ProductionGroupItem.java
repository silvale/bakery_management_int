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
 * Item thuộc 1 ProductionGroup.
 *
 * <p>{@code gramsPerUnit} chỉ dùng cho BATCH_FORMULA:
 * size 12 = 100g/cái, size 14 = 150g/cái, v.v.
 */
@Getter
@Setter
@Entity
@Table(
        name = "production_group_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "item_id"}))
public class ProductionGroupItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductionGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Gram trên 1 đơn vị — chỉ dùng cho BATCH_FORMULA. */
    @Column(name = "grams_per_unit", precision = 8, scale = 2)
    private BigDecimal gramsPerUnit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
