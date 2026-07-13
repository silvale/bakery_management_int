package com.bakery.api.production.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.bakery.api.report.entity.DailyReport;
import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.DayType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Kế hoạch sản xuất cho 1 ngày.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>DRAFT — tự động tạo khi DailyReport được finalize</li>
 *   <li>APPROVED — Chính approve → bếp thấy</li>
 *   <li>REJECTED — Chính reject → cần tạo lại</li>
 * </ol>
 *
 * <p>Trạng thái lưu trong {@code approvalStatus} từ BaseEntity.
 * Bếp chỉ query plan có approvalStatus = APPROVED.
 */
@Getter
@Setter
@Entity
@Table(name = "production_plan")
public class ProductionPlan extends BaseEntity {

    /** Ngày sản xuất (ngày mai). */
    @Column(name = "plan_date", nullable = false, unique = true)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    /** DailyReport của ngày hôm nay đã trigger tạo plan này. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_from")
    private DailyReport generatedFrom;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ProductionPlanLine> lines = new ArrayList<>();
}
