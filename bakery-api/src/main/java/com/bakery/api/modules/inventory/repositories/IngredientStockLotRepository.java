package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.IngredientStockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface IngredientStockLotRepository extends JpaRepository<IngredientStockLot, UUID> {

    /**
     * FIFO query: lấy các lô còn hàng, sắp xếp ngày nhập cũ nhất trước.
     * Đây là query cốt lõi của FIFO engine.
     */
    @Query("""
        SELECT isl FROM IngredientStockLot isl
        WHERE isl.ingredient.id = :ingredientId
          AND isl.branch.id     = :branchId
          AND isl.isDepleted    = FALSE
        ORDER BY isl.importDate ASC, isl.createdAt ASC
        """)
    List<IngredientStockLot> findAvailableLotsForFifo(
        @Param("ingredientId") UUID ingredientId,
        @Param("branchId")     UUID branchId
    );

    /** Tổng tồn kho nguyên liệu (tính từ các lô chưa hết) */
    @Query("""
        SELECT COALESCE(SUM(isl.qtyRemaining), 0)
        FROM IngredientStockLot isl
        WHERE isl.ingredient.id = :ingredientId
          AND isl.branch.id     = :branchId
          AND isl.isDepleted    = FALSE
        """)
    BigDecimal sumQtyRemaining(
        @Param("ingredientId") UUID ingredientId,
        @Param("branchId")     UUID branchId
    );

    /** Cập nhật qty_remaining sau FIFO consume */
    /**
     * MAX PRICE RULE: Lấy giá CAO NHẤT trong các lô đang còn hàng.
     * Chỉ xét lô có qty_remaining > 0 — KHÔNG lấy từ lịch sử.
     */
    @Query("""
        SELECT MAX(isl.unitPrice)
        FROM IngredientStockLot isl
        WHERE isl.ingredient.id = :ingredientId
          AND isl.branch.id     = :branchId
          AND isl.isDepleted    = FALSE
          AND isl.qtyRemaining  > 0
        """)
    java.util.Optional<BigDecimal> findMaxUnitPriceInStock(
        @Param("ingredientId") UUID ingredientId,
        @Param("branchId")     UUID branchId
    );

        @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE IngredientStockLot isl
        SET isl.qtyRemaining = :qtyRemaining,
            isl.isDepleted   = :isDepleted
        WHERE isl.id = :id
        """)
    int updateQtyRemaining(
        @Param("id")           UUID id,
        @Param("qtyRemaining") BigDecimal qtyRemaining,
        @Param("isDepleted")   boolean isDepleted
    );
}
