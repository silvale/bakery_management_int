package com.bakery.common.repository;

import com.bakery.common.entity.PosTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {

    Optional<PosTransaction> findByBranchIdAndProductIdAndTransactionDate(
        UUID branchId, UUID productId, LocalDate transactionDate
    );

    List<PosTransaction> findAllByBranchIdAndTransactionDate(UUID branchId, LocalDate transactionDate);

    /**
     * Fetch với product để tránh N+1 khi reconcile.
     */
    @Query("""
        SELECT pt FROM PosTransaction pt
        JOIN FETCH pt.product
        WHERE pt.branch.id = :branchId
          AND pt.transactionDate = :date
        ORDER BY pt.product.code
        """)
    List<PosTransaction> findAllWithProductByBranchAndDate(
        @Param("branchId") UUID branchId,
        @Param("date")     LocalDate date
    );

    /**
     * Tổng doanh thu theo ngày (dùng cho báo cáo nhanh).
     */
    @Query("""
        SELECT COALESCE(SUM(pt.revenue), 0)
        FROM PosTransaction pt
        WHERE pt.branch.id = :branchId
          AND pt.transactionDate = :date
        """)
    java.math.BigDecimal sumRevenueByBranchAndDate(
        @Param("branchId") UUID branchId,
        @Param("date")     LocalDate date
    );
}
