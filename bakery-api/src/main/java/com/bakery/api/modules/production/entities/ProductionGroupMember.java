package com.bakery.api.modules.production.entities;

import jakarta.persistence.*;
import lombok.*;
import com.bakery.api.modules.masterdata.entities.Product;

@Entity
@Table(name = "production_group_member")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionGroupMember {

    @EmbeddedId
    private ProductionGroupMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private ProductionGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
