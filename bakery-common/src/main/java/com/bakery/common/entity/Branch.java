package com.bakery.common.entity;

import com.bakery.common.entity.enums.BranchType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Chi nhánh / Kho.
 *
 * is_main    = TRUE  → Kho Tổng (nhập NL từ NCC)
 * branch_type        → KHO_TONG | KHO_BEP | SHOP
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

    /** TRUE chỉ với Kho Tổng — giữ lại để không break PurchaseOrderService */
    @Column(name = "is_main", nullable = false)
    @Builder.Default
    private Boolean isMain = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "branch_type", nullable = false, length = 20)
    @Builder.Default
    private BranchType branchType = BranchType.SHOP;
}
