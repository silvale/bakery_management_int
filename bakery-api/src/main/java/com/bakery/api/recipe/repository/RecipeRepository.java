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
}
