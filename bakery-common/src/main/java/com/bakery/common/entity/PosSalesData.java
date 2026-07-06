package com.bakery.common.entity;

import com.bakery.common.entity.enums.ItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dữ liệu bán hàng từ POS — nguồn độc lập trong 3-way reconciliation.
 *
 * Upload hàng ngày từ file xuất của máy POS.
 * Batch processor đọc file và insert vào bảng này, tách biệt với
 * inventory_transaction để 3 nguồn reconciliation độc lập nhau.
 *
 * Maps to table: pos_sales_data
 */
@Entity
@Table(
    name = "pos_sales_data",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_pos_sales_data",
        columnNames = {"sales_date", "branch_id", "item_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosSalesData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    @Builder.Default
    private ItemType itemType = ItemType.PRODUCT;

    @Column(name = "qty_sold_pos", nullable = false, precision = 10, scale = 4)
    private BigDecimal qtySoldPos;

    @Column(name = "revenue", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private OffsetDateTime uploadedAt = OffsetDateTime.now();
}
