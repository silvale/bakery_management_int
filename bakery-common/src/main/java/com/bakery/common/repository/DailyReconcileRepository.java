package com.bakery.common.repository;

import com.bakery.common.entity.DailyReconcile;
import com.bakery.common.entity.enums.ReconcileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyReconcileRepository extends JpaRepository<DailyReconcile, UUID> {

    Optional<DailyReconcile> findByBranchIdAndProductIdAndReconDate(
        UUID branchId, UUID productId, LocalDate reconDate
    );

    /**
     * Fetch kèm product (JOIN FETCH) để tránh LazyInitializationException
     * khi đọc product.code/name ngoài transaction.
     */
    @Query("""
        SELECT dr FROM DailyReconcile dr
        JOIN FETCH dr.product
        WHERE dr.branch.id = :branchId
          AND dr.reconDate = :reconDate
        ORDER BY dr.product.code
        """)
    List<DailyReconcile> findAllByBranchIdAndReconDate(
        @Param("branchId")  UUID branchId,
        @Param("reconDate") LocalDate reconDate
    );

    /** Chỉ lấy các dòng có bất thường (dùng cho dashboard cảnh báo) */
    @Query("""
        SELECT dr FROM DailyReconcile dr
        JOIN FETCH dr.product
        WHERE dr.branch.id = :branchId
          AND dr.reconDate = :reconDate
          AND dr.overallStatus <> :status
        ORDER BY dr.product.code
        """)
    List<DailyReconcile> findAllByBranchIdAndReconDateAndOverallStatusNot(
        @Param("branchId")  UUID branchId,
        @Param("reconDate") LocalDate reconDate,
        @Param("status")    ReconcileStatus status
    );

    /**
     * Tổng hợp lợi nhuận theo khoảng ngày.
     */
    @Query("""
        SELECT COALESCE(SUM(dr.grossProfit), 0)
        FROM DailyReconcile dr
        WHERE dr.branch.id = :branchId
          AND dr.reconDate BETWEEN :fromDate AND :toDate
        """)
    BigDecimal sumGrossProfitByBranchAndDateRange(
        @Param("branchId") UUID branchId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate")   LocalDate toDate
    );

    boolean existsByBranchIdAndReconDate(UUID branchId, LocalDate reconDate);

    @Modifying
    @Query("DELETE FROM DailyReconcile dr WHERE dr.branch.id = :branchId AND dr.reconDate = :reconDate")
    int deleteByBranchIdAndReconDate(
        @Param("branchId")  UUID branchId,
        @Param("reconDate") LocalDate reconDate
    );
}
