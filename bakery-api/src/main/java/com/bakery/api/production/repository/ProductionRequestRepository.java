package com.bakery.api.production.repository;

import java.time.LocalDate;
import java.util.List;

import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.framework.entity.ProductionType;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionRequestRepository extends BaseRepository<ProductionRequest> {
    List<ProductionRequest> findByProductionDate(LocalDate date);
    List<ProductionRequest> findByProductionTypeAndProductionDate(ProductionType type, LocalDate date);
    long countByCodeStartingWith(String codePrefix);
}
