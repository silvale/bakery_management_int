package com.bakery.common.entity;

import com.bakery.common.entity.enums.ReconcileStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lệnh sản xuất hàng ngày.
 * Source: BanhRaNgay.xlsx (Admin tạo → Bếp thực hiện).
 * 1 lệnh / ngày / chi nhánh.
 */
@Entity
@Table(
    name = "production_order",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_production_order_branch_date",
        columnNames = {"branch_id", "order_date"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReconcileStatus status = ReconcileStatus.PENDING;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductionOrderLine> lines = new ArrayList<>();
}
