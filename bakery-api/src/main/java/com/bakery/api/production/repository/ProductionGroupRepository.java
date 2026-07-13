package com.bakery.api.production.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionGroupRepository extends BaseRepository<ProductionGroup> {

    Optional<ProductionGroup> findByCode(String code);

    List<ProductionGroup> findByItemGroupIdOrderByCodeAsc(UUID itemGroupId);

    List<ProductionGroup> findByActiveTrue();

    /** Tìm group chứa 1 item cụ thể (dùng khi generate plan để biết item thuộc group nào). */
    Optional<ProductionGroup> findByItemsItemId(UUID itemId);
}
