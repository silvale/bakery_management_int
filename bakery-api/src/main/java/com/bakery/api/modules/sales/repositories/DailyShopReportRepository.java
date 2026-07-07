package com.bakery.api.modules.sales.repositories;

import com.bakery.api.modules.sales.entities.DailyShopReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyShopReportRepository extends JpaRepository<DailyShopReport, UUID> {

    Optional<DailyShopReport> findByReportDateAndBranchIdAndItemId(
        LocalDate reportDate, UUID branchId, UUID itemId
    );

    List<DailyShopReport> findByReportDateAndBranchId(LocalDate reportDate, UUID branchId);

    /**
     * Lấy báo cáo trong khoảng ngày — dùng cho reconciliation batch.
     */
    @Query("""
        SELECT r FROM DailyShopReport r
        WHERE r.branch.id  = :branchId
          AND r.reportDate >= :from
          AND r.reportDate <= :to
        ORDER BY r.reportDate ASC
        """)
    List<DailyShopReport> findByBranchAndDateRange(
        @Param("branchId") UUID branchId,
        @Param("from")     LocalDate from,
        @Param("to")       LocalDate to
    );

    /**
     * Tổng hủy theo item trong ngày — dùng trong v_reconciliation query.
     */
    @Query("""
        SELECT COALESCE(SUM(r.qtyDestroyedActual), 0)
        FROM DailyShopReport r
        WHERE r.branch.id  = :branchId
          AND r.itemId     = :itemId
          AND r.reportDate = :date
        """)
    java.math.BigDecimal sumDestroyedByItem(
        @Param("branchId") UUID branchId,
        @Param("itemId")   UUID itemId,
        @Param("date")     LocalDate date
    );
}
