package com.bakery.api.production.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.ProductionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Phiếu yêu cầu sản xuất bánh.
 *
 * <p>DAILY: tạo từ ProductionPlan đã APPROVED (hoặc thủ công từ manager).
 * <p>ORDER: tạo thủ công khi có đơn đặt hàng phát sinh trong ngày.
 *
 * <p>Lifecycle: DRAFT → APPROVED (trừ NL kho bếp) → IN_PROGRESS → DONE (tất cả lines COMPLETED)
 */
@Getter
@Setter
@Entity
@Table(name = "production_request")
public class ProductionRequest extends BaseEntity {

    /** Mã phiếu tự sinh, e.g., PR-DAILY-20260708-001 hoặc PR-ORDER-20260708-001 */
    @Column(name = "code", unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "production_type", nullable = false, length = 20)
    private ProductionType productionType;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "productionRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ProductionRequestLine> lines = new ArrayList<>();
}
