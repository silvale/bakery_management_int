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

    /**
     * 08/D6 (sửa 2026-11-12): giá lô gần nhất của item ở mọi kho (received_date, rồi created_at) cho điều chỉnh tăng,
     * BỎ QUA lô sinh từ phiếu kho loại ADJUSTMENT và lô có unit_cost = 0 (kể cả null). Không có → rỗng (giá 0).
     */
    @Query(value = """
            SELECT l.unit_cost FROM stock_lot l
             WHERE l.item_id = :itemId AND l.unit_cost > 0
               AND NOT EXISTS (SELECT 1 FROM stock_movement m
                                 JOIN inventory_request r ON r.id = m.ref_id
                                WHERE m.lot_id = l.id AND m.movement_type = 'IN'
                                  AND m.ref_type = 'INVENTORY_REQUEST' AND r.request_type = 'ADJUSTMENT')
             ORDER BY l.received_date DESC, l.created_at DESC
             LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<BigDecimal> findLatestNonAdjustmentUnitCost(@Param("itemId") UUID itemId);

    /** 08: tồn (tổng qty_remaining) theo item tại một kho — [itemId, sum]. Một query cho cả danh sách. */
    @Query("SELECT l.item.id, SUM(l.qtyRemaining) FROM StockLot l "
            + "WHERE l.warehouse.id = :warehouseId AND l.item.id IN :itemIds GROUP BY l.item.id")
    List<Object[]> sumRemainingByItem(@Param("warehouseId") UUID warehouseId,
                                      @Param("itemIds") java.util.Collection<UUID> itemIds);

    /** 24/BE-5: tồn chung (không tính lô đang giữ) theo kho × mặt hàng — [warehouseId, itemId, sum]. Một query cho cả trang phiếu. */
    @Query("SELECT l.warehouse.id, l.item.id, SUM(l.qtyRemaining) FROM StockLot l "
            + "WHERE l.warehouse.id IN :warehouseIds AND l.item.id IN :itemIds AND l.reservedForRequestId IS NULL "
            + "GROUP BY l.warehouse.id, l.item.id")
    List<Object[]> sumFreeByWarehouseItem(@Param("warehouseIds") java.util.Collection<UUID> warehouseIds,
                                          @Param("itemIds") java.util.Collection<UUID> itemIds);

    /** FIFO trong một kho: lô còn hàng, nhập sớm nhất trước. */
    List<StockLot> findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
            UUID itemId, UUID warehouseId, BigDecimal minQty);

    List<StockLot> findByWarehouseCode(String warehouseCode);

    /**
     * Tổng tồn kho theo item × warehouse — gộp tất cả lot còn hàng.
     * Trả Object[]: [itemCode, itemName, warehouseCode, warehouseName, totalQtyRemaining,
     *                itemUnit, itemClass, itemGroupCode, itemGroupName, minStockQuantity, restockQuantity]
     */
    @Query("""
            SELECT i.code, i.name,
                   s.warehouse.code, s.warehouse.name,
                   SUM(s.qtyRemaining),
                   i.unit, TYPE(i), g.code, g.name, i.minStockQuantity, i.restockQuantity
            FROM StockLot s JOIN s.item i LEFT JOIN i.itemGroup g
            WHERE s.qtyRemaining > 0
              AND (:warehouseCode IS NULL OR s.warehouse.code = :warehouseCode)
            GROUP BY i.id, i.code, i.name, i.unit, TYPE(i), g.code, g.name, i.minStockQuantity, i.restockQuantity,
                     s.warehouse.id, s.warehouse.code, s.warehouse.name
            ORDER BY i.code, s.warehouse.code
            """)
    List<Object[]> findStockSummaryRaw(@Param("warehouseCode") String warehouseCode);

    /** 24: lô ở kho bảng tạm đang giữ cho phiếu SX (idx_stock_lot_wh_reserved). */
    List<StockLot> findByWarehouseIdAndReservedForRequestId(UUID warehouseId, UUID requestId);

    /** 24: mọi lô còn hàng của một kho (kiểm bảng tạm lúc đóng phiên; idx_stock_lot_warehouse_remaining). */
    List<StockLot> findByWarehouseIdAndQtyRemainingGreaterThan(UUID warehouseId, java.math.BigDecimal qty);
}
