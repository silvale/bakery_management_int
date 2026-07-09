package com.bakery.api.master.repository;

import java.util.Optional;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.repository.BaseRepository;

/**
 * Repository cho Item bất kể type — dùng khi cần lookup theo ID
 * mà không biết trước item_type (e.g., ProductExpiryConfig, InventoryRequestLine).
 */
public interface ItemLookupRepository extends BaseRepository<Item> {
    Optional<Item> findByCode(String code);
}
