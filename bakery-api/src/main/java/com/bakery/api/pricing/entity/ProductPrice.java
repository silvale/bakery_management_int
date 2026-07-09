package com.bakery.api.pricing.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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

@Getter
@Setter
@Entity
@Table(name = "product_price")
public class ProductPrice extends BaseEntity {

    /** FK về item (item_type = PRODUCT) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}
