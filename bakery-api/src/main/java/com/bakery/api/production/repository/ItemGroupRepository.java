package com.bakery.api.production.repository;

import java.util.Optional;

import com.bakery.api.production.entity.ItemGroup;
import com.bakery.framework.repository.BaseRepository;

public interface ItemGroupRepository extends BaseRepository<ItemGroup> {
    Optional<ItemGroup> findByCode(String code);
}
