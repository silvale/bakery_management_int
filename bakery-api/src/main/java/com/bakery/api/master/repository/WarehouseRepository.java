package com.bakery.api.master.repository;

import java.util.Optional;

import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.repository.BaseRepository;

public interface WarehouseRepository extends BaseRepository<Warehouse> {
    Optional<Warehouse> findByCode(String code);
}
