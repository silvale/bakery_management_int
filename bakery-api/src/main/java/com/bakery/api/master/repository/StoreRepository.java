package com.bakery.api.master.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.entity.Store;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends BaseRepository<Store> {

    boolean existsByCode(String code);

    /** Số kho theo cửa hàng, mọi trạng thái → [storeId, count] (idx_warehouse_store). */
    @Query(value = "SELECT store_id, COUNT(*) FROM warehouse WHERE store_id IS NOT NULL GROUP BY store_id", nativeQuery = true)
    List<Object[]> countWarehousesByStore();

    @Query(value = "SELECT COUNT(*) FROM warehouse WHERE store_id = :id", nativeQuery = true)
    long countWarehouses(@Param("id") UUID id);
}
