package com.bakery.api.inventory.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.framework.repository.BaseRepository;

public interface InventoryRequestLineRepository extends BaseRepository<InventoryRequestLine> {
    List<InventoryRequestLine> findByInventoryRequestIdOrderBySortOrderAsc(UUID inventoryRequestId);
}
