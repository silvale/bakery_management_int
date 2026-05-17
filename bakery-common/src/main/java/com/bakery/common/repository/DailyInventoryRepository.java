package com.bakery.common.repository;

import com.bakery.common.entity.DailyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyInventoryRepository extends JpaRepository<DailyInventory, UUID> {

    Optional<DailyInventory> findByBranchIdAndProductIdAndInventoryDate(
        UUID branchId, UUID productId, LocalDate inventoryDate
    );

    List<DailyInventory> findAllByBranchIdAndInventoryDate(UUID branchId, LocalDate inventoryDate);

    /**
     * Lấy qty_closing của ngày hôm qua → dùng làm qty_opening hôm nay.
     */
    @Query("""
        SELECT di FROM DailyInventory di
        WHERE di.branch.id = :branchId
          AND di.product.id = :productId
          AND di.inventoryDate = :date
        """)
    Optional<DailyInventory> findPreviousDayInventory(
        @Param("branchId")  UUID branchId,
        @Param("productId") UUID productId,
        @Param("date")      LocalDate date
    );

    /**
     * Fetch với product để tránh N+1 khi xử lý reconcile.
     */
    @Query("""
        SELECT di FROM DailyInventory di
        JOIN FETCH di.product
        WHERE di.branch.id = :branchId
          AND di.inventoryDate = :inventoryDate
        """)
    List<DailyInventory> findAllWithProductByBranchIdAndDate(
        @Param("branchId")      UUID branchId,
        @Param("inventoryDate") LocalDate inventoryDate
    );
}
