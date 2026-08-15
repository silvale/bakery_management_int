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

    Optional<ItemPackaging> findByItemIdAndIsDefaultTrue(UUID itemId);

    @Modifying
    @Query("DELETE FROM ItemPackaging p WHERE p.item.id = :itemId")
    void deleteByItemId(UUID itemId);
}
