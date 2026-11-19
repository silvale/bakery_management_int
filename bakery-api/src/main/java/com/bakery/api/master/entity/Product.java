package com.bakery.api.master.entity;

import org.hibernate.envers.Audited;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Audited   // 10/BE-3: lớp con của Item phải có @Audited, nếu không Envers bỏ qua mọi thay đổi (item_his luôn trống)
@Entity
@DiscriminatorValue("PRODUCT")
public class Product extends Item {
}
