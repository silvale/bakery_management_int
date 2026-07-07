package com.bakery.api.modules.masterdata.entities;

import jakarta.persistence.*;
import lombok.*;
import com.bakery.api.framework.BaseEntity;

@Entity
@Table(name = "product_expiry_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductExpiryConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /** Số ngày sử dụng kể từ ngày sản xuất. VD: 1 = hết hạn ngày hôm sau */
    @Column(name = "shelf_days", nullable = false)
    @Builder.Default
    private Integer shelfDays = 1;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
