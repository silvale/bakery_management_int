package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.ProductMapping;
import com.bakery.framework.repository.BaseRepository;

public interface ProductMappingRepository extends BaseRepository<ProductMapping> {

    List<ProductMapping> findByItemId(UUID itemId);

    Optional<ProductMapping> findByExCode(String exCode);

    boolean existsByExCode(String exCode);
}
