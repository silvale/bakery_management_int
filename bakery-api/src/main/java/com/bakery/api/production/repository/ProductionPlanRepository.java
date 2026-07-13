package com.bakery.api.production.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionPlanRepository extends BaseRepository<ProductionPlan> {

    Optional<ProductionPlan> findByPlanDate(LocalDate planDate);

    List<ProductionPlan> findByApprovalStatusOrderByPlanDateDesc(ApprovalStatus status);

    boolean existsByPlanDate(LocalDate planDate);
}
