package com.bakery.api.report.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.report.entity.DailyReportLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReportLineRepository extends JpaRepository<DailyReportLine, UUID> {
    @org.springframework.data.jpa.repository.Query("SELECT l FROM DailyReportLine l JOIN FETCH l.item WHERE l.dailyReport.id = :reportId")
    List<DailyReportLine> findByDailyReportIdWithItem(@org.springframework.data.repository.query.Param("reportId") UUID reportId);

    List<DailyReportLine> findByDailyReportId(UUID dailyReportId);
    Optional<DailyReportLine> findByDailyReportIdAndItemId(UUID dailyReportId, UUID itemId);
}
