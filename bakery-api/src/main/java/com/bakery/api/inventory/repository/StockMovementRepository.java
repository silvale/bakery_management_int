package com.bakery.api.inventory.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.framework.repository.BaseRepository;

public interface StockMovementRepository extends BaseRepository<StockMovement> {
    List<StockMovement> findByLotIdOrderByCreatedAtDesc(UUID lotId);
    List<StockMovement> findByRefIdAndRefType(UUID refId, String refType);
}
