package com.bakery.api.master.dto;

import java.util.List;
import java.util.UUID;

/** 22/BE-3: PUT /items/default-warehouse. */
public record ItemDefaultWarehouseRequest(List<UUID> itemIds, UUID warehouseId) {}
