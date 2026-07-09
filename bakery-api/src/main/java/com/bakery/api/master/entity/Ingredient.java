package com.bakery.api.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("INGREDIENT")
public class Ingredient extends Item {

    /** Code value key: INGREDIENT_TYPE */
    @Column(name = "ingredient_type", length = 50)
    private String ingredientType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_supplier_id")
    private Supplier defaultSupplier;
}
