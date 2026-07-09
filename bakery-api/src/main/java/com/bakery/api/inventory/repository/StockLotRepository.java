package com.bakery.api.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.framework.repository.BaseRepository;

public interface StockLotRepository extends BaseRepository<StockLot> {
    /** FIFO: lấy lot còn hàng, sắp xếp theo ngày nhập sớm nhất */
    List<StockLot> findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
            UUID itemId, BigDecimal minQty);

    List<StockLot> findByItemIdAndWarehouseId(UUID itemId, UUID warehouseId);
}
