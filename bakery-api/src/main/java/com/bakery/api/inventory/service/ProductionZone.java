package com.bakery.api.inventory.service;

import com.bakery.api.master.entity.Warehouse;

/**
 * Kho nhận lô đầu ra của sản xuất (22: theo cấu hình kho, không theo mã). Danh sách kho sản xuất lấy NL do
 * {@code WarehouseRoles} trả; trừ NL ở {@link ProductionStock}.
 */
public final class ProductionZone {

    private ProductionZone() {}

    /**
     * BTP có kho mặc định được phép lưu BTP thì vào kho đó; còn lại (và mọi thành phẩm: bánh làm xong chờ Bán Hàng nhận)
     * vào kho bánh chờ giao.
     */
    public static Warehouse outputWarehouse(boolean semiProduct, Warehouse defaultWarehouse, Warehouse deliveryHold) {
        return semiProduct && defaultWarehouse != null && defaultWarehouse.isCanStoreSemi() ? defaultWarehouse : deliveryHold;
    }
}
