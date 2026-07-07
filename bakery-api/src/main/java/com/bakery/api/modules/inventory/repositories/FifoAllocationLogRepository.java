package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.FifoAllocationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FifoAllocationLogRepository extends JpaRepository<FifoAllocationLog, UUID> {

    List<FifoAllocationLog> findAllByProductionLotId(UUID productionLotId);

    /** Tìm log chưa recalculate (dùng khi backdate) */
    List<FifoAllocationLog> findAllByProductionLotIdAndIsRecalculatedFalse(UUID productionLotId);
}
