package com.bakery.api.master.repository;

import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.framework.repository.BaseRepository;

public interface ProductExpiryConfigRepository extends BaseRepository<ProductExpiryConfig> {
    Optional<ProductExpiryConfig> findByItemId(UUID itemId);
}
