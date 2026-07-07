package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {
    Optional<InventoryAdjustment> findByCode(String code);
    List<InventoryAdjustment> findAllByStatusOrderByCreatedAtDesc(String status);
    List<InventoryAdjustment> findAllByBranchIdOrderByCreatedAtDesc(UUID branchId);
    List<InventoryAdjustment> findAllByBranchIdAndStatusOrderByCreatedAtDesc(UUID branchId, String status);
    long countByCodeStartingWith(String prefix);
}
