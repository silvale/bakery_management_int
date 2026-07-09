package com.bakery.api.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLotRepository extends BaseRepository<StockLot> {

    /** FIFO: lấy lot còn hàng, sắp xếp theo ngày nhập sớm nhất */
    List<StockLot> findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
            UUID itemId, BigDecimal minQty);

    List<StockLot> findByItemIdAndWarehouseId(UUID itemId, UUID warehouseId);

    List<StockLot> findByWarehouseCode(String warehouseCode);

    /**
     * Tổng tồn kho theo item × warehouse — gộp tất cả lot còn hàng.
     * Trả Object[]: [itemCode, itemName, warehouseCode, warehouseName, totalQtyRemaining]
     */
    @Query("""
            SELECT s.item.code, s.item.name,
                   s.warehouse.code, s.warehouse.name,
                   SUM(s.qtyRemaining)
            FROM StockLot s
            WHERE s.qtyRemaining > 0
              AND (:warehouseCode IS NULL OR s.warehouse.code = :warehouseCode)
            GROUP BY s.item.id, s.item.code, s.item.name,
                     s.warehouse.id, s.warehouse.code, s.warehouse.name
            ORDER BY s.item.code, s.warehouse.code
            """)
    List<Object[]> findStockSummaryRaw(@Param("warehouseCode") String warehouseCode);
}
