package com.bakery.api.master.repository;

import java.util.Optional;

import com.bakery.api.master.entity.Supplier;
import com.bakery.framework.repository.BaseRepository;

public interface SupplierRepository extends BaseRepository<Supplier> {
    Optional<Supplier> findByCode(String code);

    /** NCC "Mua lẻ / trả ngay" đang dùng — điền sẵn vào NCC xem giá (11/D3). */
    java.util.List<Supplier> findByCashPurchaseTrueAndStatus(com.bakery.framework.entity.EntityStatus status);
}
