package com.bakery.api.master.entity;

import org.hibernate.envers.Audited;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Audited   // 10/BE-3: lớp con của Item phải có @Audited, nếu không Envers bỏ qua mọi thay đổi (item_his luôn trống)
@Entity
@DiscriminatorValue("INGREDIENT")
public class Ingredient extends Item {

    @Audited(targetAuditMode = org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_supplier_id")
    private Supplier defaultSupplier;
}
