package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.MovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 24/§4.3: chuyển lô FIFO giữa hai kho, GIỮ {@code unit_cost} của lô nguồn. Dùng chung cho TRANSFER, PICK, RETURN
 * (tách từ {@code InventoryRequestService.approveTransfer}). Không ném lỗi khi thiếu: trả phần còn thiếu.
 */
@Component
@RequiredArgsConstructor
public class StockTransfer {

    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * Chuyển {@code qty} (đơn vị kho của item) từ {@code source} sang {@code target}, ghi OUT ở nguồn và IN ở đích.
     *
     * @param source       kho nguồn; null = mọi kho (hành vi cũ của TRANSFER không có kho xuất)
     * @param fromReserved chỉ trừ lô đang giữ cho phiếu này; null = chỉ lô chung (không giữ cho ai)
     * @param holdFor      lô mới ở kho đích được giữ cho phiếu SX này; null = lô chung
     * @return phần còn thiếu (0 nếu đủ). Caller tự quyết ném lỗi; transaction rollback thì không đổi gì.
     */
    public BigDecimal move(Item item, BigDecimal qty, Warehouse source, Warehouse target, UUID fromReserved,
                           UUID holdFor, LocalDate receivedDate, String refType, UUID refId, String outNote, String inNote) {
        List<StockLot> lots = source != null
                ? stockLotRepository.findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                        item.getId(), source.getId(), BigDecimal.ZERO)
                : stockLotRepository.findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                        item.getId(), BigDecimal.ZERO);
        BigDecimal remaining = qty;
        for (StockLot lot : eligible(lots, fromReserved)) {
            if (remaining.signum() <= 0) break;
            BigDecimal deduct = remaining.min(lot.getQtyRemaining());
            lot.setQtyRemaining(lot.getQtyRemaining().subtract(deduct));
            stockLotRepository.save(lot);

            StockMovement out = new StockMovement();
            out.setLot(lot);
            out.setMovementType(MovementType.OUT);
            out.setQty(deduct);
            out.setRefId(refId);
            out.setRefType(refType);
            out.setNote(outNote);
            stockMovementRepository.save(out);

            // Lô mới ở kho đích, giữ nguyên unitCost của lô gốc
            StockLot in = new StockLot();
            in.setItem(lot.getItem());
            in.setWarehouse(target);
            in.setSupplier(lot.getSupplier());
            in.setQtyInitial(deduct);
            in.setQtyRemaining(deduct);
            in.setUnitCost(lot.getUnitCost());
            in.setReceivedDate(receivedDate);
            in.setExpiryDate(lot.getExpiryDate());
            in.setReservedForRequestId(holdFor);
            StockLot savedIn = stockLotRepository.save(in);

            StockMovement inMv = new StockMovement();
            inMv.setLot(savedIn);
            inMv.setMovementType(MovementType.IN);
            inMv.setQty(deduct);
            inMv.setRefId(refId);
            inMv.setRefType(refType);
            inMv.setNote(inNote);
            stockMovementRepository.save(inMv);

            remaining = remaining.subtract(deduct);
        }
        return remaining.max(BigDecimal.ZERO);
    }

    /** Lô được phép trừ: đúng nhóm giữ ({@code null} = lô chung). Hàm thuần để unit test. */
    static List<StockLot> eligible(List<StockLot> lots, UUID fromReserved) {
        return lots.stream().filter(l -> Objects.equals(l.getReservedForRequestId(), fromReserved)).toList();
    }
}
