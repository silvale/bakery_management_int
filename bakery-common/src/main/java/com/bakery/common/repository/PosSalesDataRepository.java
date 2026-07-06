package com.bakery.common.repository;

import com.bakery.common.entity.PosSalesData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PosSalesDataRepository extends JpaRepository<PosSalesData, UUID> {

    Optional<PosSalesData> findBySalesDateAndBranchIdAndItemId(
        LocalDate salesDate, UUID branchId, UUID itemId
    );

    List<PosSalesData> findBySalesDateAndBranchId(LocalDate salesDate, UUID branchId);

    /**
     * Lấy dữ liệu POS trong khoảng ngày cho 1 chi nhánh.
     */
    @Query("""
        SELECT p FROM PosSalesData p
        WHERE p.branch.id  = :branchId
          AND p.salesDate >= :from
          AND p.salesDate <= :to
        ORDER BY p.salesDate ASC
        """)
    List<PosSalesData> findByBranchAndDateRange(
        @Param("branchId") UUID branchId,
        @Param("from")     LocalDate from,
        @Param("to")       LocalDate to
    );

    /**
     * Tổng doanh số theo item trong ngày — dùng trong reconciliation.
     */
    @Query("""
        SELECT COALESCE(SUM(p.qtySoldPos), 0)
        FROM PosSalesData p
        WHERE p.branch.id = :branchId
          AND p.itemId    = :itemId
          AND p.salesDate = :date
        """)
    java.math.BigDecimal sumSoldByItem(
        @Param("branchId") UUID branchId,
        @Param("itemId")   UUID itemId,
        @Param("date")     LocalDate date
    );

    /**
     * Tổng doanh thu trong ngày — dùng cho báo cáo tài chính.
     */
    @Query("""
        SELECT COALESCE(SUM(p.revenue), 0)
        FROM PosSalesData p
        WHERE p.branch.id = :branchId
          AND p.salesDate = :date
        """)
    java.math.BigDecimal sumRevenueByBranchAndDate(
        @Param("branchId") UUID branchId,
        @Param("date")     LocalDate date
    );
}
