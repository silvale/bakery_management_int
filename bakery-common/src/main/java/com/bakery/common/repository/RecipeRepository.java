package com.bakery.common.repository;

import com.bakery.common.entity.Recipe;
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

    @Query("SELECT COALESCE(MAX(r.version), 0) FROM Recipe r WHERE r.product.id = :productId")
    int findMaxVersion(@Param("productId") UUID productId);
}
