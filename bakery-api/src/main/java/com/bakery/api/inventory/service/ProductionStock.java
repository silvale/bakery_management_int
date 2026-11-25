package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.bakery.framework.entity.MovementType;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.service.WarehouseRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 22/§5.2: trừ NL khi hoàn thành phiếu SX DAILY/ORDER và phụ kiện mẫu bánh (tạm, phần B bỏ). Một hàm dùng chung.
 * Trừ FIFO ở kho nhóm 1 (sản xuất lấy NL, không nhận NCC); còn thiếu mới trừ nhóm 2 (sản xuất lấy NL, nhận NCC).
 */
@Component
@RequiredArgsConstructor
public class ProductionStock {

    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRoles roles;

    /**
     * Trừ {@code need} của {@code itemId}, ghi movement OUT. Trả phần còn thiếu (0 nếu đủ); không bao giờ để lô âm.
     * Caller tự quyết ném lỗi hay cảnh báo.
     */
    public BigDecimal deduct(UUID itemId, BigDecimal need, String refType, UUID refId, String note) {
        List<StockLot> lots = stockLotRepository
                .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(itemId, BigDecimal.ZERO);
        return deductGroups(lots, ids(roles.productionPrimary()), ids(roles.productionFallback()), need, (lot, taken) -> {
            StockMovement mv = new StockMovement();
            mv.setLot(lot);
            mv.setMovementType(MovementType.OUT);
            mv.setQty(taken);
            mv.setRefType(refType);
            mv.setRefId(refId);
            mv.setNote(note);
            stockMovementRepository.save(mv);
            stockLotRepository.save(lot);
        });
    }

    /** Phần thuần: lô đã xếp FIFO; nhóm 1 trước, nhóm 2 cho phần còn thiếu. */
    static BigDecimal deductGroups(List<StockLot> fifoLots, Set<UUID> primary, Set<UUID> fallback,
                                   BigDecimal need, BiConsumer<StockLot, BigDecimal> onDeduct) {
        BigDecimal left = StockFifo.deduct(inGroup(fifoLots, primary), need, onDeduct);
        if (left.signum() > 0) left = StockFifo.deduct(inGroup(fifoLots, fallback), left, onDeduct);
        return left;
    }

    private static List<StockLot> inGroup(List<StockLot> lots, Set<UUID> warehouseIds) {
        return lots.stream().filter(l -> l.getWarehouse() != null && warehouseIds.contains(l.getWarehouse().getId())).toList();
    }

    private static Set<UUID> ids(List<Warehouse> ws) {
        return ws.stream().map(Warehouse::getId).collect(Collectors.toSet());
    }
}
