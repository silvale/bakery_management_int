package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.StockLot;
import com.bakery.api.framework.enums.StockLotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockLotRepository extends JpaRepository<StockLot, UUID> {

    Optional<StockLot> findByBarcode(String barcode);

    /** Lấy các lô còn hàng của 1 nguyên liệu, sắp xếp FIFO (nhập trước xuất trước) */
    @Query("""
        SELECT sl FROM StockLot sl
        WHERE sl.ingredient.id = :ingredientId
          AND sl.branch.id     = :branchId
          AND sl.status        = 'AVAILABLE'
        ORDER BY sl.receivedDate ASC, sl.createdAt ASC
        """)
    List<StockLot> findAvailableByIngredientAndBranch(
        @Param("ingredientId") UUID ingredientId,
        @Param("branchId")     UUID branchId
    );

    /** Cảnh báo lô sắp hết hạn trong N ngày */
    @Query("""
        SELECT sl FROM StockLot sl
        JOIN FETCH sl.ingredient
        WHERE sl.status      = 'AVAILABLE'
          AND sl.expiryDate IS NOT NULL
          AND sl.expiryDate <= :warningDate
        ORDER BY sl.expiryDate ASC
        """)
    List<StockLot> findExpiringBefore(@Param("warningDate") LocalDate warningDate);

    List<StockLot> findAllByIngredientIdAndStatus(UUID ingredientId, StockLotStatus status);
}
