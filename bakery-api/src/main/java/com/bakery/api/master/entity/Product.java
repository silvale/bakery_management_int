package com.bakery.api.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("PRODUCT")
public class Product extends Item {

    /** Code value key: PRODUCT_TYPE */
    @Column(name = "product_type", length = 50)
    private String productType;
}
