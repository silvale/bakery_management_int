package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 24/§5.1: chia kho cho phiếu lấy tự tạo. Hàm thuần, không đụng DB.
 * Mỗi mặt hàng cần N: lấy ở kho lưu trữ trước (tối đa bằng tồn), thiếu thì sang các kho Sản xuất lấy NL khác theo thứ
 * tự kho, vẫn thiếu thì để phần thiếu ở dòng của kho lưu trữ kèm {@code stockShort}.
 */
public final class PickPlanner {

    private PickPlanner() {}

    /** {@code homeId} = kho lưu trữ (NL) / kho mặc định (BTP); null = chưa có. */
    public record Want(UUID itemId, BigDecimal qty, UUID homeId) {}

    /** Một dòng phiếu lấy đề xuất ở kho {@code warehouseId}. */
    public record Alloc(UUID warehouseId, UUID itemId, BigDecimal qty, boolean stockShort) {}

    /**
     * @param sourceOrder kho Sản xuất lấy NL theo thứ tự kho (không có kho bảng tạm)
     * @param stock       tồn hiện có (kho, mặt hàng)
     */
    public static List<Alloc> allocate(List<Want> wants, List<UUID> sourceOrder, BiFunction<UUID, UUID, BigDecimal> stock) {
        List<Alloc> out = new ArrayList<>();
        for (Want w : wants) {
            if (w.qty() == null || w.qty().signum() <= 0) continue;
            UUID home = w.homeId() != null ? w.homeId() : (sourceOrder.isEmpty() ? null : sourceOrder.get(0));
            if (home == null) throw new IllegalStateException("Chưa cấu hình kho Sản xuất lấy NL");
            BigDecimal left = w.qty();
            BigDecimal fromHome = left.min(avail(stock, home, w.itemId()));
            left = left.subtract(fromHome);
            List<Alloc> others = new ArrayList<>();
            for (UUID wh : sourceOrder) {
                if (left.signum() <= 0) break;
                if (wh.equals(home)) continue;
                BigDecimal take = left.min(avail(stock, wh, w.itemId()));
                if (take.signum() > 0) {
                    others.add(new Alloc(wh, w.itemId(), take, false));
                    left = left.subtract(take);
                }
            }
            // phần thiếu ở lại dòng của kho lưu trữ
            BigDecimal homeQty = fromHome.add(left);
            if (homeQty.signum() > 0) out.add(new Alloc(home, w.itemId(), homeQty, left.signum() > 0));
            out.addAll(others);
        }
        return out;
    }

    private static BigDecimal avail(BiFunction<UUID, UUID, BigDecimal> stock, UUID wh, UUID item) {
        BigDecimal v = stock.apply(wh, item);
        return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
    }
}
