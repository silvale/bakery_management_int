package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionPlanLineRepository extends BaseRepository<ProductionPlanLine> {

    List<ProductionPlanLine> findByPlanIdOrderBySortOrderAsc(UUID planId);

    List<ProductionPlanLine> findByPlanIdAndGroupIdOrderBySortOrderAsc(UUID planId, UUID groupId);
}
