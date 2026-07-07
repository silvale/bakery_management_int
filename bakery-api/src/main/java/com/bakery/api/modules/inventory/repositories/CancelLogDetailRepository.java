package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.CancelLogDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CancelLogDetailRepository extends JpaRepository<CancelLogDetail, UUID> {

    List<CancelLogDetail> findAllByCancelLogId(UUID cancelLogId);

    List<CancelLogDetail> findAllByProductionLotId(UUID productionLotId);
}
