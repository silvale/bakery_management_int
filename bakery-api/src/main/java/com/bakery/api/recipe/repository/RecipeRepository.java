/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.recipe.entity.Recipe;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends BaseRepository<Recipe> {

    List<Recipe> findByProductId(UUID productId);

    List<Recipe> findBySemiProductId(UUID semiProductId);

    Optional<Recipe> findByProductIdAndActiveTrue(UUID productId);

    Optional<Recipe> findBySemiProductIdAndActiveTrue(UUID semiProductId);

    /** Lấy version cao nhất hiện có cho 1 product (dùng để auto-increment version mới). */
    @Query("SELECT COALESCE(MAX(r.version), 0) FROM Recipe r WHERE r.product.id = :productId")
    int maxVersionByProduct(@Param("productId") UUID productId);

    /** Lấy version cao nhất hiện có cho 1 semi-product. */
    @Query("SELECT COALESCE(MAX(r.version), 0) FROM Recipe r WHERE r.semiProduct.id = :semiProductId")
    int maxVersionBySemiProduct(@Param("semiProductId") UUID semiProductId);

    /** Deactivate tất cả recipe đang active của 1 product (trước khi active recipe mới). */
    @Modifying
    @Query("UPDATE Recipe r SET r.active = false WHERE r.product.id = :productId AND r.active = true")
    void deactivateAllByProduct(@Param("productId") UUID productId);

    /** Deactivate tất cả recipe đang active của 1 semi-product. */
    @Modifying
    @Query("UPDATE Recipe r SET r.active = false WHERE r.semiProduct.id = :semiProductId AND r.active = true")
    void deactivateAllBySemiProduct(@Param("semiProductId") UUID semiProductId);

    /** Phiên bản mới nhất của recipe cho 1 product (dùng trong toResponse). */
    Optional<Recipe> findFirstByProductIdOrderByVersionDesc(UUID productId);

    /** Phiên bản PENDING_APPROVAL mới nhất của recipe cho 1 product (dùng để upsert khi update product). */
    Optional<Recipe> findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
            UUID productId, com.bakery.framework.entity.ApprovalStatus approvalStatus);

    /** Phiên bản mới nhất của recipe cho 1 semi-product (dùng trong toResponse). */
    Optional<Recipe> findFirstBySemiProductIdOrderByVersionDesc(UUID semiProductId);

    /** Phiên bản PENDING_APPROVAL mới nhất của recipe cho 1 semi-product (dùng để upsert khi update). */
    Optional<Recipe> findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
            UUID semiProductId, com.bakery.framework.entity.ApprovalStatus approvalStatus);
    /**
     * Tìm tất cả recipe line trong active recipe có đơn vị KHÔNG khớp với item.unit
     * VÀ không có bản ghi unit_conversion nào để quy đổi.
     * Kết quả dùng để chẩn đoán lỗi UNIT_MISMATCH khi tính giá cost.
     *
     * Columns: product_code, product_name, product_type,
     *          ingredient_code, ingredient_name, ingredient_unit, recipe_unit
     */
    @Query(value = """
        SELECT
            p.code            AS product_code,
            p.name            AS product_name,
            p.item_type       AS product_type,
            i.code            AS ingredient_code,
            i.name            AS ingredient_name,
            i.unit            AS ingredient_unit,
            rl.unit           AS recipe_unit
        FROM recipe_line rl
        JOIN recipe r  ON r.id  = rl.recipe_id
        JOIN item   i  ON i.id  = rl.item_id
        JOIN item   p  ON p.id  = COALESCE(r.product_id, r.semi_product_id)
        WHERE r.is_active = true
          AND UPPER(rl.unit) <> UPPER(i.unit)
          AND NOT EXISTS (
              SELECT 1 FROM unit_conversion uc
              WHERE UPPER(uc.from_unit) = UPPER(rl.unit)
                AND UPPER(uc.to_unit)   = UPPER(i.unit)
          )
        ORDER BY p.code, i.code
        """, nativeQuery = true)
    List<Object[]> findUnitMismatchIssues();

    /** Lấy tất cả active recipe để bulk-fill yieldQuantity. */
    List<Recipe> findByActiveTrue();

    /**
     * Tìm tất cả active recipe có chứa ingredient/semi-product với itemId cho trước.
     * Dùng cho tính năng "Sản phẩm nào dùng NL/BTP này?"
     */
    @Query("SELECT r FROM Recipe r JOIN r.lines l WHERE l.item.id = :itemId AND r.active = true")
    List<Recipe> findActiveRecipesUsingItem(@Param("itemId") UUID itemId);

}
