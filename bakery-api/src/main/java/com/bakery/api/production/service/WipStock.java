package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.inventory.service.StockFifo;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.service.WarehouseRoles;
import com.bakery.framework.entity.MovementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 24/§5.5, §5.7: hoàn thành phiếu SX chỉ trừ ở kho bảng tạm (lô giữ cho phiếu trước, rồi lô chung); nhả lô giữ khi
 * phiếu hết hạn dùng.
 */
@Component
@RequiredArgsConstructor
public class WipStock {

    static final String REF_TYPE = "PRODUCTION_REQUEST";

    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRoles roles;

    // ── Phần thuần (unit test) ──────────────────────────────────────────────

    /** Lô trừ được cho phiếu {@code prId}: giữ cho phiếu này hoặc lô chung. Không gồm lô giữ cho phiếu khác. */
    static BigDecimal available(List<StockLot> lots, UUID prId) {
        return lots.stream().filter(l -> usable(l, prId)).map(StockLot::getQtyRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Trừ FIFO: lô giữ cho phiếu này trước, rồi lô chung. Trả phần thiếu. Lô giữ cho phiếu khác không bị chạm. */
    static BigDecimal deduct(List<StockLot> fifoLots, UUID prId, BigDecimal need, BiConsumer<StockLot, BigDecimal> onDeduct) {
        List<StockLot> held = fifoLots.stream().filter(l -> prId.equals(l.getReservedForRequestId())).toList();
        List<StockLot> common = fifoLots.stream().filter(l -> l.getReservedForRequestId() == null).toList();
        BigDecimal left = StockFifo.deduct(held, need, onDeduct);
        return left.signum() > 0 ? StockFifo.deduct(common, left, onDeduct) : left;
    }

    private static boolean usable(StockLot l, UUID prId) {
        return l.getReservedForRequestId() == null || l.getReservedForRequestId().equals(prId);
    }

    /** Lý do thiếu NL, null nếu đủ. {@code unit} có thể null. */
    static String shortageMessage(String name, BigDecimal missing, String unit) {
        return "Chưa lấy đủ nguyên liệu: " + name + " thiếu " + missing.stripTrailingZeros().toPlainString()
                + (unit == null ? "" : " " + unit) + ". Lấy thêm NL.";
    }

    // ── Dùng DB ─────────────────────────────────────────────────────────────

    /**
     * Trừ {@code needs} ở kho bảng tạm cho phiếu {@code pr}, ghi OUT {@code PRODUCTION_REQUEST}. Kiểm đủ mọi NL TRƯỚC khi
     * động tồn; thiếu → IllegalStateException (422) và không đổi gì.
     */
    @Transactional
    public void consume(UUID prId, String prCode, Map<UUID, ProductionNeeds.Need> needs) {
        if (needs.isEmpty()) return;
        Warehouse wip = roles.workInProgress();
        Map<UUID, List<StockLot>> lotsByItem = new java.util.LinkedHashMap<>();
        for (var n : needs.values()) {
            List<StockLot> lots = stockLotRepository
                    .findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                            n.item().getId(), wip.getId(), BigDecimal.ZERO);
            BigDecimal avail = available(lots, prId);
            if (avail.compareTo(n.qty()) < 0) {
                throw new IllegalStateException(shortageMessage(n.item().getName(), n.qty().subtract(avail), n.item().getUnit()));
            }
            lotsByItem.put(n.item().getId(), lots);
        }
        for (var n : needs.values()) {
            deduct(lotsByItem.get(n.item().getId()), prId, n.qty(), (lot, taken) -> {
                StockMovement mv = new StockMovement();
                mv.setLot(lot);
                mv.setMovementType(MovementType.OUT);
                mv.setQty(taken);
                mv.setRefType(REF_TYPE);
                mv.setRefId(prId);
                mv.setNote("Xuất NL sản xuất từ phiếu " + prCode);
                stockMovementRepository.save(mv);
                stockLotRepository.save(lot);
            });
        }
    }

    /** Lô còn hàng ở kho bảng tạm đang giữ cho phiếu {@code prId} → lô chung (hết hạn dùng: hoàn thành hết, hủy, từ chối). */
    @Transactional
    public int release(UUID prId) {
        Warehouse wip;
        try { wip = roles.workInProgress(); } catch (IllegalStateException e) { return 0; }   // chưa cấu hình bảng tạm thì không có gì để nhả
        List<StockLot> held = new ArrayList<>(stockLotRepository.findByWarehouseIdAndReservedForRequestId(wip.getId(), prId));
        for (StockLot l : held) {
            l.setReservedForRequestId(null);
            stockLotRepository.save(l);
        }
        return held.size();
    }
}
