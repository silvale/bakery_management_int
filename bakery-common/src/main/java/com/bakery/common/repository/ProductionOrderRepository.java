package com.bakery.common.repository;

import com.bakery.common.entity.ProductionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {

    Optional<ProductionOrder> findByBranchIdAndOrderDate(UUID branchId, LocalDate orderDate);

    boolean existsByBranchIdAndOrderDate(UUID branchId, LocalDate orderDate);

    /**
     * Fetch eager lines để tránh N+1 khi xử lý reconcile.
     */
    @Query("""
        SELECT po FROM ProductionOrder po
        JOIN FETCH po.lines pol
        JOIN FETCH pol.product
        WHERE po.branch.id = :branchId
          AND po.orderDate = :orderDate
        """)
    Optional<ProductionOrder> findWithLinesByBranchIdAndOrderDate(
        @Param("branchId")   UUID branchId,
        @Param("orderDate")  LocalDate orderDate
    );
}
