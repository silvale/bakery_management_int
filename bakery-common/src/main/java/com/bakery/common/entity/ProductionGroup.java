package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Nhóm sản phẩm dùng chung phôi bánh — phục vụ GROUP_SUBTRACT pattern.
 *
 * Ví dụ: Nhóm Pana (Pana chanh dây + Pana việt quất + Pana dâu)
 *   → cùng dùng SF-PHOI-PANA làm đế
 *   → tính tổng tồn nhóm rồi trừ khỏi target_nhóm → số phôi cần làm
 *
 * Code: GR_PANA, GR_BANH_KEM_S16, ...
 */
@Entity
@Table(name = "production_group")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_code", nullable = false, unique = true, length = 50)
    private String groupCode;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    /** FK mềm → semi_product.code (phôi dùng chung cho cả nhóm) */
    @Column(name = "main_semi_product_code", nullable = false, length = 50)
    private String mainSemiProductCode;

    @Column(name = "weekday_target", nullable = false, precision = 12, scale = 3)
    private BigDecimal weekdayTarget = BigDecimal.ZERO;

    @Column(name = "weekend_target", nullable = false, precision = 12, scale = 3)
    private BigDecimal weekendTarget = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductionGroupMember> members = new ArrayList<>();
}
