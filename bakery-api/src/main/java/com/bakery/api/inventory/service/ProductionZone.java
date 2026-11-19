package com.bakery.api.inventory.service;

import java.util.List;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.master.entity.Warehouse;

/**
 * "Khu sản xuất" = kho KITCHEN + kho COLD (Phòng lạnh). Sản xuất lấy nguyên liệu và BTP ở cả hai,
 * không cần phiếu điều chuyển. Tìm theo mã kho; môi trường không có kho COLD thì chỉ còn KITCHEN.
 */
public final class ProductionZone {

    public static final String KITCHEN = "KITCHEN";
    public static final String COLD = "COLD";
    public static final List<String> CODES = List.of(KITCHEN, COLD);

    private ProductionZone() {}

    public static boolean contains(Warehouse w) {
        return w != null && CODES.contains(w.getCode());
    }

    public static boolean contains(StockLot lot) {
        return lot != null && contains(lot.getWarehouse());
    }

    /**
     * Kho nhận lô đầu ra của sản xuất. Chỉ BTP có kho mặc định thuộc khu sản xuất mới vào kho đó;
     * còn lại (và mọi thành phẩm PRODUCT, vì giao bếp → cửa hàng lấy lô KITCHEN) vào KITCHEN.
     */
    public static Warehouse outputWarehouse(boolean semiProduct, Warehouse defaultWarehouse, Warehouse kitchen) {
        return semiProduct && contains(defaultWarehouse) ? defaultWarehouse : kitchen;
    }
}
