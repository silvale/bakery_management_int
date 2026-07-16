package com.bakery.api.production.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.framework.repository.BaseRepository;

public interface DeliveryRecordRepository extends BaseRepository<DeliveryRecord> {
    Optional<DeliveryRecord> findByProductionRequestLineId(UUID lineId);

    /** Tất cả DeliveryRecord của ngày sản xuất — dùng khi tổng hợp DailyReport */
    List<DeliveryRecord> findByProductionRequestLine_ProductionRequest_ProductionDate(LocalDate productionDate);

    /** Delivery records trong khoảng ngày — dùng để tính cancel list */
    List<DeliveryRecord> findByProductionRequestLine_ProductionRequest_ProductionDateBetween(
            LocalDate from, LocalDate to);
}
