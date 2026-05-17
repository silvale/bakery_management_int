package com.bakery.common.repository;

import com.bakery.common.entity.FifoAllocationLog;
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
