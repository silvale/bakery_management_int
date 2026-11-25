package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends BaseRepository<Warehouse> {
    Optional<Warehouse> findByCode(String code);

    /** 22: số lô còn tồn > 0 theo kho → [warehouseId, count] (idx_stock_lot_warehouse_remaining). */
    @Query(value = "SELECT warehouse_id, COUNT(*) FROM stock_lot WHERE qty_remaining > 0 AND warehouse_id IS NOT NULL GROUP BY warehouse_id", nativeQuery = true)
    List<Object[]> countStockLotsByWarehouse();

    /** 22: số nhân viên được gán theo kho (idx_user_warehouse_wh). */
    @Query(value = "SELECT warehouse_id, COUNT(*) FROM user_warehouse GROUP BY warehouse_id", nativeQuery = true)
    List<Object[]> countUsersByWarehouse();

    /** 22: số mặt hàng đang dùng có kho mặc định = kho này, theo kho (idx_item_default_warehouse). */
    @Query(value = "SELECT default_warehouse_id, COUNT(*) FROM item WHERE default_warehouse_id IS NOT NULL AND status = 'ACTIVE' GROUP BY default_warehouse_id", nativeQuery = true)
    List<Object[]> countItemsByWarehouse();

    @Query(value = "SELECT COUNT(*) FROM stock_lot WHERE warehouse_id = :id AND qty_remaining > 0", nativeQuery = true)
    long countStockLots(@Param("id") UUID id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM stock_lot WHERE warehouse_id = :id)", nativeQuery = true)
    boolean hasAnyStockLot(@Param("id") UUID id);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM inventory_request WHERE source_warehouse_id = :id)
                OR EXISTS (SELECT 1 FROM inventory_request WHERE target_warehouse_id = :id)""", nativeQuery = true)
    boolean hasAnyRequest(@Param("id") UUID id);

    /** Mặt hàng ACTIVE có kho mặc định = kho này, theo loại (INGREDIENT | SEMI_PRODUCT | PRODUCT) → số lượng. */
    @Query(value = "SELECT COUNT(*) FROM item WHERE default_warehouse_id = :id AND status = 'ACTIVE' AND item_type = :type", nativeQuery = true)
    long countItemsByDefaultWarehouseAndType(@Param("id") UUID id, @Param("type") String type);

    @Query(value = "SELECT COUNT(*) FROM item WHERE default_warehouse_id = :id AND status = 'ACTIVE'", nativeQuery = true)
    long countItemsByDefaultWarehouse(@Param("id") UUID id);
}
