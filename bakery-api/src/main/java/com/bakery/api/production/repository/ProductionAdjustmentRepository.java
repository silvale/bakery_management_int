package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.framework.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionAdjustmentRepository extends JpaRepository<ProductionAdjustment, UUID> {
    List<ProductionAdjustment> findByDeliveryRecordId(UUID deliveryRecordId);
    boolean existsByDeliveryRecordIdAndApprovalStatusIn(UUID deliveryRecordId, List<ApprovalStatus> statuses);
}
