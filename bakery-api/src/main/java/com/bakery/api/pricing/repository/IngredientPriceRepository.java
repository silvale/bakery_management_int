package com.bakery.api.pricing.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.framework.repository.BaseRepository;

public interface IngredientPriceRepository extends BaseRepository<IngredientPrice> {
    List<IngredientPrice> findByItemIdOrderByEffectiveDateDesc(UUID itemId);
    List<IngredientPrice> findByItemIdAndSupplierIdOrderByEffectiveDateDesc(UUID itemId, UUID supplierId);
}
