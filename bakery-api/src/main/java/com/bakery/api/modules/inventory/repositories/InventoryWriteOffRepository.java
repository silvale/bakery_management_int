package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.InventoryWriteOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryWriteOffRepository extends JpaRepository<InventoryWriteOff, UUID> {
    Optional<InventoryWriteOff> findByCode(String code);
    List<InventoryWriteOff> findAllByStatusOrderByCreatedAtDesc(String status);
    List<InventoryWriteOff> findAllByBranchIdOrderByCreatedAtDesc(UUID branchId);
    List<InventoryWriteOff> findAllByBranchIdAndStatusOrderByCreatedAtDesc(UUID branchId, String status);
    long countByCodeStartingWith(String prefix);
}
