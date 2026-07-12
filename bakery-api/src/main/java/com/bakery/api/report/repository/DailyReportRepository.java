package com.bakery.api.report.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.report.entity.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReportRepository extends JpaRepository<DailyReport, UUID> {
    Optional<DailyReport> findByReportDate(LocalDate reportDate);
}
