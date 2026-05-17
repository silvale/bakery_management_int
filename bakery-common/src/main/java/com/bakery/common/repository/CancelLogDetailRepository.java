package com.bakery.common.repository;

import com.bakery.common.entity.CancelLogDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CancelLogDetailRepository extends JpaRepository<CancelLogDetail, UUID> {

    List<CancelLogDetail> findAllByCancelLogId(UUID cancelLogId);

    List<CancelLogDetail> findAllByProductionLotId(UUID productionLotId);
}
