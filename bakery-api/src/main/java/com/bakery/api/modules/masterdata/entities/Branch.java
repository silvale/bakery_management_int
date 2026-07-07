package com.bakery.api.modules.masterdata.entities;

import com.bakery.api.framework.BaseAdminEntity;
import com.bakery.api.framework.enums.BranchType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

/**
 * Chi nhánh / Kho.
 * branch_type: KHO_TONG | KHO_BEP | SHOP
 * is_main: TRUE chỉ với Kho Tổng (nhập NL từ NCC)
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch extends BaseAdminEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_main", nullable = false)
    @Builder.Default
    private Boolean isMain = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "branch_type", nullable = false, length = 20, columnDefinition = "branch_type")
    @Builder.Default
    private BranchType branchType = BranchType.SHOP;
}
