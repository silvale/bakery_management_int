package com.bakery.api.inventory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import com.bakery.api.inventory.entity.StockLot;

/** 25: tiền và quyền xem giá của phiếu xuất ngoài. Hàm thuần để unit test. */
public final class ExportCost {

    private ExportCost() {}

    /** Số đã trừ và thành tiền (Σ số trừ × giá nhập của chính lô bị trừ), còn thiếu bao nhiêu. */
    public record Result(BigDecimal amount, BigDecimal remaining) {}

    /** Trừ FIFO {@code qty} khỏi các lô (đã xếp cũ trước), mỗi lô gọi {@code onDeduct}; thành tiền làm tròn 2 số lẻ. */
    public static Result deduct(List<StockLot> fifoLots, BigDecimal qty, BiConsumer<StockLot, BigDecimal> onDeduct) {
        BigDecimal[] amount = {BigDecimal.ZERO};
        BigDecimal left = StockFifo.deduct(fifoLots, qty, (lot, taken) -> {
            amount[0] = amount[0].add(taken.multiply(lot.getUnitCost()));
            onDeduct.accept(lot, taken);
        });
        return new Result(amount[0].setScale(2, RoundingMode.HALF_UP), left);
    }

    /** Đơn giá theo đơn vị dòng = thành tiền ÷ số lượng dòng, 4 số lẻ; số lượng 0 → null. */
    public static BigDecimal unitCost(BigDecimal amount, BigDecimal lineQuantity) {
        if (lineQuantity == null || lineQuantity.signum() <= 0) return null;
        return amount.divide(lineQuantity, 4, RoundingMode.HALF_UP);
    }

    /**
     * Dòng hiện giá khi user xem được giá của MỌI NCC có lô bị trừ. {@code supplierIds} có thể chứa null (lô không NCC:
     * BTP, hàng điều chỉnh) → {@code canSee.test(null)} (chỉ Admin). Không có lô nào (thiếu dữ liệu) → như không NCC.
     */
    public static boolean priceVisible(Collection<UUID> supplierIds, Predicate<UUID> canSee) {
        if (supplierIds == null || supplierIds.isEmpty()) return canSee.test(null);
        for (UUID s : supplierIds) if (!canSee.test(s)) return false;
        return true;
    }
}
