package com.bakery.api.master.repository;

import java.util.Optional;

import com.bakery.api.master.entity.Supplier;
import com.bakery.framework.repository.BaseRepository;

public interface SupplierRepository extends BaseRepository<Supplier> {
    Optional<Supplier> findByCode(String code);
}
