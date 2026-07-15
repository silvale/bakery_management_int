package com.bakery.api.report.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.bakery.framework.entity.DailyReportStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Header báo cáo cuối ngày.
 * Mỗi ngày chỉ có 1 report (report_date UNIQUE).
 * Admin bấm FINALIZE → không ai chỉnh sửa được nữa.
 */
@Getter
@Setter
@Entity
@Table(name = "daily_report")
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DailyReportStatus status = DailyReportStatus.DRAFT;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by", length = 100)
    private String finalizedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @JsonIgnore
    @OneToMany(mappedBy = "dailyReport", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DailyReportLine> lines = new ArrayList<>();
}
