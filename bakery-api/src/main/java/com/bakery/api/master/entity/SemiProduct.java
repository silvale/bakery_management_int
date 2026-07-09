package com.bakery.api.master.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("SEMI_PRODUCT")
public class SemiProduct extends Item {
    // Không có field riêng — code, name, unit kế thừa từ Item
}
