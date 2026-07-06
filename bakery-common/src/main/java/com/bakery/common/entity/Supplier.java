package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supplier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier extends BaseAdminEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
