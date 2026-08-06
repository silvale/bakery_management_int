package com.bakery.api.report.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.report.entity.CancelRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancelRecordRepository extends JpaRepository<CancelRecord, UUID> {

    List<CancelRecord> findByDailyReportIdOrderByExCodeAsc(UUID reportId);

    /** Tìm record EXPIRED duy nhất per (report, exCode) */
    @Query("SELECT c FROM CancelRecord c WHERE c.dailyReport.id = :reportId AND c.exCode = :exCode AND c.cancelType = 'EXPIRED'")
    Optional<CancelRecord> findExpiredByReportAndExCode(@Param("reportId") UUID reportId, @Param("exCode") String exCode);

    /** Tổng qty_cancel_actual theo item_id trong 1 report (để cập nhật DailyReportLine) */
    @Query("SELECT COALESCE(SUM(c.qtyCancelActual), 0) FROM CancelRecord c WHERE c.dailyReport.id = :reportId AND c.item.id = :itemId AND c.confirmed = true")
    java.math.BigDecimal sumActualCancelByReportAndItem(@Param("reportId") UUID reportId, @Param("itemId") UUID itemId);
}
