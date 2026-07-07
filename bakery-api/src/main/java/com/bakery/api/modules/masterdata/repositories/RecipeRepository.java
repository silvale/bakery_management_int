package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    /**
     * Lookup công thức tại ngày X (dùng khi tính cost hoặc re-run báo cáo):
     * Lấy version có effective_date <= targetDate, mới nhất.
     */
    @Query("""
        SELECT r FROM Recipe r
        WHERE r.product.id = :productId
          AND r.effectiveDate <= :targetDate
        ORDER BY r.effectiveDate DESC
        LIMIT 1
        """)
    Optional<Recipe> findActiveRecipe(
        @Param("productId")  UUID productId,
        @Param("targetDate") LocalDate targetDate
    );

    Optional<Recipe> findByProductIdAndIsActiveTrue(UUID productId);

    /**
     * Lấy active recipe kèm lines (JOIN FETCH tránh N+1 trong toResponse()).
     */
    @Query("""
        SELECT r FROM Recipe r
        LEFT JOIN FETCH r.lines l
        LEFT JOIN FETCH l.ingredient
        LEFT JOIN FETCH l.semiProduct
        WHERE r.product.id = :productId AND r.isActive = true
        """)
    Optional<Recipe> findActiveWithLines(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(MAX(r.version), 0) FROM Recipe r WHERE r.product.id = :productId")
    int findMaxVersion(@Param("productId") UUID productId);
}
