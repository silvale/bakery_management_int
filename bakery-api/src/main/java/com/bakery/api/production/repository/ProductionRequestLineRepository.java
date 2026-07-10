package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionRequestLineRepository extends BaseRepository<ProductionRequestLine> {
    List<ProductionRequestLine> findByProductionRequestId(UUID productionRequestId);
}
