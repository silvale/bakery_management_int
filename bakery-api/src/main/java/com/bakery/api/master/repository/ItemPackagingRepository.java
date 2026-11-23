/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.ItemPackaging;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemPackagingRepository extends JpaRepository<ItemPackaging, UUID> {

    @EntityGraph(attributePaths = "unit")   // 16: tên quy cách = unit.name, nạp cùng query (tránh N+1)
    List<ItemPackaging> findByItemIdOrderByQtyPerPackAsc(UUID itemId);

    /** Quy cách của nhiều item trong một query (12/BE-1). */
    @EntityGraph(attributePaths = "unit")
    List<ItemPackaging> findByItemIdInOrderByQtyPerPackAsc(java.util.Collection<UUID> itemIds);

    Optional<ItemPackaging> findByItemIdAndIsDefaultTrue(UUID itemId);

    /** Quy cách mặc định của nhiều item (theo mã) trong một query — dùng cho /stock-lots/summary. */
    @Query("SELECT p FROM ItemPackaging p JOIN FETCH p.item JOIN FETCH p.unit WHERE p.isDefault = true AND p.item.code IN :codes")
    List<ItemPackaging> findDefaultByItemCodes(java.util.Collection<String> codes);

    /** 16/D5: quy cách đã có dòng phiếu trỏ tới (index packaging_id). */
    @Query("SELECT DISTINCT l.packaging.id FROM InventoryRequestLine l WHERE l.packaging.id IN :ids")
    List<UUID> findIdsUsedInRequestLines(java.util.Collection<UUID> ids);

    /** 16/D5: quy cách đã có lô tồn trỏ tới (index packaging_id). */
    @Query("SELECT DISTINCT s.packaging.id FROM StockLot s WHERE s.packaging.id IN :ids")
    List<UUID> findIdsUsedInStockLots(java.util.Collection<UUID> ids);
}
