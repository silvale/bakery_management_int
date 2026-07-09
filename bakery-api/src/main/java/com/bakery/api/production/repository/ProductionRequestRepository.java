package com.bakery.api.production.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionRequestRepository extends BaseRepository<ProductionRequest> {
    List<ProductionRequest> findByWarehouseIdAndRequestDate(UUID warehouseId, LocalDate requestDate);
}
