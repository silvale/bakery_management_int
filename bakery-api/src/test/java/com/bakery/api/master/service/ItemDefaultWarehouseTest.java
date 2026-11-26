package com.bakery.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.master.entity.Warehouse;
import org.junit.jupiter.api.Test;

/** 22/BE-3: kho mặc định hợp loại mặt hàng. */
class ItemDefaultWarehouseTest {

    private static Warehouse wh(boolean prod, boolean semi) {
        Warehouse w = new Warehouse();
        w.setProductionSource(prod);
        w.setCanStoreSemi(semi);
        return w;
    }

    @Test void ingredientNeedsProductionSourceWarehouse() {
        String msg = "Nguyên liệu phải chọn kho lưu trữ (kho sản xuất lấy NL)";
        assertThat(ItemService.defaultWarehouseBlocker("INGREDIENT", null)).isEqualTo(msg);
        assertThat(ItemService.defaultWarehouseBlocker("INGREDIENT", wh(false, true))).isEqualTo(msg);   // Kho Tổng / kho chỉ lưu BTP
        assertThat(ItemService.defaultWarehouseBlocker("INGREDIENT", wh(true, false))).isNull();         // Kho Chính
        assertThat(ItemService.defaultWarehouseBlocker("INGREDIENT", wh(true, true))).isNull();          // Phòng Lạnh
    }

    @Test void semiNeedsStoreSemiWarehouse() {
        assertThat(ItemService.defaultWarehouseBlocker("SEMI_PRODUCT", null)).isNotNull();
        assertThat(ItemService.defaultWarehouseBlocker("SEMI_PRODUCT", wh(true, false))).isNotNull();   // Kho Chính không lưu BTP
        assertThat(ItemService.defaultWarehouseBlocker("SEMI_PRODUCT", wh(true, true))).isNull();
    }

    @Test void productIsUnconstrained() {
        assertThat(ItemService.defaultWarehouseBlocker("PRODUCT", null)).isNull();
        assertThat(ItemService.defaultWarehouseBlocker("PRODUCT", wh(false, false))).isNull();
    }

    @Test void wipWarehouseCannotBeStorageOrDefault() {   // 24/BE-1
        Warehouse wip = new Warehouse();
        wip.setWorkInProgress(true);
        assertThat(ItemService.defaultWarehouseBlocker("INGREDIENT", wip)).contains("Kho bảng tạm");
        assertThat(ItemService.defaultWarehouseBlocker("SEMI_PRODUCT", wip)).contains("Kho bảng tạm");
    }
}
