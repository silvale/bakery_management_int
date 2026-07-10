package com.bakery.api.production.repository;

import java.util.Optional;
import java.util.UUID;

import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.framework.repository.BaseRepository;

public interface DeliveryRecordRepository extends BaseRepository<DeliveryRecord> {
    Optional<DeliveryRecord> findByProductionRequestLineId(UUID lineId);
}
