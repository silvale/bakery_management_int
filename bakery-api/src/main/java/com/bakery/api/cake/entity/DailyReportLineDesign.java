package com.bakery.api.cake.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.api.report.entity.DailyReportLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Số liệu cuối ngày theo mẫu của một dòng thành phẩm trong báo cáo ngày. */
@Getter
@Setter
@Entity
@Table(name = "daily_report_line_design")
public class DailyReportLineDesign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private DailyReportLine line;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cake_design_id", nullable = false)
    private CakeDesign cakeDesign;

    @Column(name = "qty_opening", precision = 10, scale = 3)
    private BigDecimal qtyOpening;

    @Column(name = "qty_received", precision = 10, scale = 3)
    private BigDecimal qtyReceived;

    @Column(name = "qty_remaining_actual", precision = 10, scale = 3)
    private BigDecimal qtyRemainingActual;

    @Column(name = "qty_cancelled", precision = 10, scale = 3)
    private BigDecimal qtyCancelled;

    @Column(name = "qty_sold_implied", precision = 10, scale = 3)
    private BigDecimal qtySoldImplied;
}
