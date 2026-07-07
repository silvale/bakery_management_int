package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.IngredientStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientStockRepository extends JpaRepository<IngredientStock, UUID> {

    Optional<IngredientStock> findByIngredientIdAndBranchId(UUID ingredientId, UUID branchId);

    List<IngredientStock> findAllByBranchId(UUID branchId);

    /** Tồn kho toàn bộ nguyên liệu của 1 kho, kèm thông tin ingredient */
    @Query("""
        SELECT ist FROM IngredientStock ist
        JOIN FETCH ist.ingredient i
        WHERE ist.branch.id = :branchId
        ORDER BY i.name
        """)
    List<IngredientStock> findAllWithIngredientByBranchId(@Param("branchId") UUID branchId);

    /** Nguyên liệu sắp hết (dưới ngưỡng minQty) */
    @Query("""
        SELECT ist FROM IngredientStock ist
        JOIN FETCH ist.ingredient
        WHERE ist.branch.id   = :branchId
          AND ist.qtyOnHand   <= :minQty
        ORDER BY ist.qtyOnHand ASC
        """)
    List<IngredientStock> findLowStock(
        @Param("branchId") UUID branchId,
        @Param("minQty")   BigDecimal minQty
    );

    /** Cập nhật tồn kho trực tiếp (dùng sau nhập/xuất) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE IngredientStock ist
        SET ist.qtyOnHand   = ist.qtyOnHand + :delta,
            ist.lastUpdated = CURRENT_TIMESTAMP
        WHERE ist.ingredient.id = :ingredientId
          AND ist.branch.id     = :branchId
        """)
    int updateStock(
        @Param("ingredientId") UUID ingredientId,
        @Param("branchId")     UUID branchId,
        @Param("delta")        BigDecimal delta  // dương = nhập, âm = xuất
    );
}
