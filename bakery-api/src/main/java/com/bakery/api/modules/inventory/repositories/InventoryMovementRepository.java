package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    List<InventoryMovement> findAllByBranchIdOrderByCreatedAtDesc(UUID branchId);

    List<InventoryMovement> findAllByIngredientIdAndBranchIdOrderByCreatedAtDesc(
            UUID ingredientId, UUID branchId);

    List<InventoryMovement> findAllByProductIdAndBranchIdOrderByCreatedAtDesc(
            UUID productId, UUID branchId);

    @Query("""
        SELECT m FROM InventoryMovement m
        WHERE m.sourceType = :sourceType AND m.sourceId = :sourceId
        ORDER BY m.createdAt ASC
        """)
    List<InventoryMovement> findAllBySource(@Param("sourceType") String sourceType,
                                            @Param("sourceId")   UUID sourceId);

    List<InventoryMovement> findAllByBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID branchId, OffsetDateTime from, OffsetDateTime to);
}
