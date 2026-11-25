package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.master.entity.Warehouse;
import org.junit.jupiter.api.Test;

/** 22: kho đầu ra theo cờ của kho, không theo mã. */
class ProductionZoneTest {

    private static Warehouse wh(String name, boolean storeSemi) {
        Warehouse w = new Warehouse();
        w.setName(name);
        w.setCanStoreSemi(storeSemi);
        return w;
    }

    @Test void semiProductGoesToItsDefaultWhenWarehouseStoresSemi() {
        Warehouse hold = wh("hold", true), cold = wh("cold", true);
        assertThat(ProductionZone.outputWarehouse(true, cold, hold)).isSameAs(cold);
        assertThat(ProductionZone.outputWarehouse(true, hold, hold)).isSameAs(hold);
    }

    @Test void semiProductWithoutOrNonStoringDefaultGoesToDeliveryHold() {
        Warehouse hold = wh("hold", true);
        assertThat(ProductionZone.outputWarehouse(true, null, hold)).isSameAs(hold);
        assertThat(ProductionZone.outputWarehouse(true, wh("shop", false), hold)).isSameAs(hold);
        assertThat(ProductionZone.outputWarehouse(true, wh("main", false), hold)).isSameAs(hold);
    }

    @Test void finishedProductAlwaysGoesToDeliveryHold() {
        Warehouse hold = wh("hold", true);
        // thành phẩm chờ giao ra bán hàng: không cất Phòng Lạnh dù item có kho mặc định lưu BTP
        assertThat(ProductionZone.outputWarehouse(false, wh("cold", true), hold)).isSameAs(hold);
    }
}
