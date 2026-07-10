package com.bakery.api.recipe.entity;

import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.SemiProduct;
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
@Table(name = "recipe")
public class Recipe extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semi_product_id")
    private SemiProduct semiProduct;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "note", length = 500)
    private String note;

    /**
     * NULL = công thức gốc (base).
     * Non-null = bản sao custom từ recipe gốc (full snapshot).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_recipe_id")
    private Recipe parentRecipe;
}
