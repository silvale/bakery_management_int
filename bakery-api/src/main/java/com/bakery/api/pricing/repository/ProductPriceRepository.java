package com.bakery.api.pricing.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.pricing.entity.ProductPrice;
import com.bakery.framework.repository.BaseRepository;

public interface ProductPriceRepository extends BaseRepository<ProductPrice> {
    List<ProductPrice> findByItemIdOrderByEffectiveDateDesc(UUID itemId);
}
