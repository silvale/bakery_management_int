package com.bakery.api.inventory.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.framework.repository.BaseRepository;

public interface StockMovementRepository extends BaseRepository<StockMovement> {
    List<StockMovement> findByLotIdOrderByCreatedAtDesc(UUID lotId);
    List<StockMovement> findByRefIdAndRefType(UUID refId, String refType);

    /** 25: NCC của các lô bị trừ bởi phiếu xuất → [refId, itemId, supplierId (có thể null)] (idx_stock_movement_ref). */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT m.ref_id, l.item_id, l.supplier_id FROM stock_movement m JOIN stock_lot l ON l.id = m.lot_id
            WHERE m.movement_type = 'OUT' AND m.ref_type = 'INVENTORY_REQUEST' AND m.ref_id IN (:ids)""", nativeQuery = true)
    List<Object[]> exportSuppliers(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
