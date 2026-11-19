/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.ItemPackaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemPackagingRepository extends JpaRepository<ItemPackaging, UUID> {

    List<ItemPackaging> findByItemIdOrderByQtyPerPackAsc(UUID itemId);

    /** Quy cách của nhiều item trong một query (12/BE-1). */
    List<ItemPackaging> findByItemIdInOrderByQtyPerPackAsc(java.util.Collection<UUID> itemIds);

    Optional<ItemPackaging> findByItemIdAndIsDefaultTrue(UUID itemId);

    /** Quy cách mặc định của nhiều item (theo mã) trong một query — dùng cho /stock-lots/summary. */
    @Query("SELECT p FROM ItemPackaging p JOIN FETCH p.item WHERE p.isDefault = true AND p.item.code IN :codes")
    List<ItemPackaging> findDefaultByItemCodes(java.util.Collection<String> codes);

    @Modifying
    @Query("DELETE FROM ItemPackaging p WHERE p.item.id = :itemId")
    void deleteByItemId(UUID itemId);
}
