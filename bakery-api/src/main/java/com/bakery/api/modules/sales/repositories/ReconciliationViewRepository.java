package com.bakery.api.modules.sales.repositories;

import com.bakery.api.modules.sales.entities.ReconciliationViewEntry;
import com.bakery.api.modules.sales.entities.ReconciliationViewId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository cho view v_reconciliation (read-only).
 * Dùng @Subselect entity nên không cần native query — JPQL dùng tên entity.
 */
@Repository
public interface ReconciliationViewRepository
        extends JpaRepository<ReconciliationViewEntry, ReconciliationViewId> {

    /** Toàn bộ reconciliation trong 1 ngày (tất cả branch) */
    List<ReconciliationViewEntry> findByReconDate(LocalDate reconDate);

    /** Reconciliation 1 ngày + 1 branch cụ thể */
    List<ReconciliationViewEntry> findByReconDateAndBranchId(LocalDate reconDate, UUID branchId);

    /** Khoảng ngày — tất cả branch */
    @Query("""
        SELECT r FROM ReconciliationViewEntry r
        WHERE r.reconDate >= :from
          AND r.reconDate <= :to
        ORDER BY r.reconDate ASC, r.branchId ASC
        """)
    List<ReconciliationViewEntry> findByDateRange(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to
    );

    /** Khoảng ngày + branch cụ thể */
    @Query("""
        SELECT r FROM ReconciliationViewEntry r
        WHERE r.reconDate >= :from
          AND r.reconDate <= :to
          AND r.branchId  = :branchId
        ORDER BY r.reconDate ASC
        """)
    List<ReconciliationViewEntry> findByDateRangeAndBranch(
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to,
            @Param("branchId") UUID branchId
    );

    /** Các row có variance != 0 (có chênh lệch) trong 1 ngày */
    @Query("""
        SELECT r FROM ReconciliationViewEntry r
        WHERE r.reconDate = :date
          AND r.variance  <> 0
        ORDER BY r.branchId ASC
        """)
    List<ReconciliationViewEntry> findDiscrepanciesByDate(@Param("date") LocalDate date);
}
