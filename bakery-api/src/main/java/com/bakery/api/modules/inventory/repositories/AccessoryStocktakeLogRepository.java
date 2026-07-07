package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.AccessoryStocktakeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessoryStocktakeLogRepository extends JpaRepository<AccessoryStocktakeLog, UUID> {

    /** Lần kiểm đếm gần nhất của 1 nguyên liệu tại 1 kho */
    @Query("""
        SELECT asl FROM AccessoryStocktakeLog asl
        WHERE asl.branch.id     = :branchId
          AND asl.ingredient.id = :ingredientId
        ORDER BY asl.stocktakeDate DESC
        """)
    List<AccessoryStocktakeLog> findAllByBranchAndIngredientOrderByDateDesc(
        @Param("branchId")     UUID branchId,
        @Param("ingredientId") UUID ingredientId
    );

    /** Toàn bộ lịch sử kiểm đếm của 1 kho */
    @Query("""
        SELECT asl FROM AccessoryStocktakeLog asl
        JOIN FETCH asl.ingredient i
        WHERE asl.branch.id   = :branchId
          AND asl.stocktakeDate >= :fromDate
        ORDER BY asl.stocktakeDate DESC, i.name
        """)
    List<AccessoryStocktakeLog> findAllByBranchAndDateAfter(
        @Param("branchId")  UUID      branchId,
        @Param("fromDate")  LocalDate fromDate
    );
}
