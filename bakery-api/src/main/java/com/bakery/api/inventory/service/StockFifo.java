package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiConsumer;

import com.bakery.api.inventory.entity.StockLot;

/** Trừ FIFO không ném lỗi khi thiếu: trừ phần có, trả phần thiếu. */
public final class StockFifo {

    private StockFifo() {}

    /**
     * Trừ {@code qty} khỏi các lô theo thứ tự đã sắp, mỗi lô gọi {@code onDeduct(lot, qtyTrừ)}.
     * Trả phần thiếu (0 nếu đủ). Không bao giờ để lô âm.
     */
    public static BigDecimal deduct(List<StockLot> lots, BigDecimal qty, BiConsumer<StockLot, BigDecimal> onDeduct) {
        BigDecimal left = qty;
        for (StockLot lot : lots) {
            if (left.signum() <= 0) break;
            BigDecimal take = lot.getQtyRemaining().min(left);
            if (take.signum() <= 0) continue;
            lot.setQtyRemaining(lot.getQtyRemaining().subtract(take));
            onDeduct.accept(lot, take);
            left = left.subtract(take);
        }
        return left.max(BigDecimal.ZERO);
    }
}
