package com.bakery.common.repository;

import com.bakery.common.entity.CancelLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface CancelLogRepository extends JpaRepository<CancelLog, UUID> {

    List<CancelLog> findAllByBranchIdAndCancelDate(UUID branchId, LocalDate date);

    /** Chỉ lấy các lần hủy có cảnh báo */
    @Query("""
        SELECT cl FROM CancelLog cl
        JOIN FETCH cl.product
        WHERE cl.branch.id   = :branchId
          AND cl.cancelDate  = :date
          AND cl.cancelStatus != 'OK'
        ORDER BY cl.product.code
        """)
    List<CancelLog> findWarningsByBranchAndDate(
        @Param("branchId") UUID branchId,
        @Param("date")     LocalDate date
    );

    /** Tổng cost hủy trong ngày */
    @Query("""
        SELECT COALESCE(SUM(cl.cancelledCost), 0)
        FROM CancelLog cl
        WHERE cl.branch.id  = :branchId
          AND cl.cancelDate = :date
        """)
    java.math.BigDecimal sumCancelledCostByDate(
        @Param("branchId") UUID branchId,
        @Param("date")     LocalDate date
    );
}
