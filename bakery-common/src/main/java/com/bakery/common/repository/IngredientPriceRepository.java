package com.bakery.common.repository;

import com.bakery.common.entity.IngredientPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientPriceRepository extends JpaRepository<IngredientPrice, UUID> {

    /**
     * Lookup giá đúng tại ngày X:
     * Lấy version có effective_date <= targetDate, mới nhất.
     */
    @Query("""
        SELECT ip FROM IngredientPrice ip
        WHERE ip.ingredient.id = :ingredientId
          AND ip.effectiveDate <= :targetDate
        ORDER BY ip.effectiveDate DESC
        LIMIT 1
        """)
    Optional<IngredientPrice> findActivePrice(
        @Param("ingredientId") UUID ingredientId,
        @Param("targetDate")   LocalDate targetDate
    );

    /**
     * Lấy version mới nhất hiện tại (dùng khi tính lại cost).
     */
    @Query("""
        SELECT ip FROM IngredientPrice ip
        WHERE ip.ingredient.id = :ingredientId
        ORDER BY ip.effectiveDate DESC
        LIMIT 1
        """)
    Optional<IngredientPrice> findLatestPrice(@Param("ingredientId") UUID ingredientId);

    @Query("SELECT COALESCE(MAX(ip.version), 0) FROM IngredientPrice ip WHERE ip.ingredient.id = :ingredientId")
    int findMaxVersion(@Param("ingredientId") UUID ingredientId);
}
