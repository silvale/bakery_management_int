package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.master.entity.Warehouse;
import org.junit.jupiter.api.Test;

class ProductionZoneTest {

    private static Warehouse wh(String code) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        return w;
    }

    private static StockLot lotAt(String code) {
        StockLot l = new StockLot();
        l.setWarehouse(wh(code));
        return l;
    }

    @Test void zoneIsKitchenAndCold() {
        assertThat(ProductionZone.contains(lotAt("KITCHEN"))).isTrue();
        assertThat(ProductionZone.contains(lotAt("COLD"))).isTrue();
    }

    @Test void otherWarehousesAreOutsideZone() {
        assertThat(ProductionZone.contains(lotAt("MAIN"))).isFalse();
        assertThat(ProductionZone.contains(lotAt("SHOP"))).isFalse();
        assertThat(ProductionZone.contains(lotAt("COLD2"))).isFalse();
        assertThat(ProductionZone.contains(new StockLot())).isFalse(); // lô không có kho
        assertThat(ProductionZone.contains((StockLot) null)).isFalse();
    }

    @Test void semiProductGoesToItsColdOrKitchenDefault() {
        Warehouse kitchen = wh("KITCHEN"), cold = wh("COLD");
        assertThat(ProductionZone.outputWarehouse(true, cold, kitchen)).isSameAs(cold);
        assertThat(ProductionZone.outputWarehouse(true, kitchen, kitchen)).isSameAs(kitchen);
    }

    @Test void semiProductWithoutOrOutOfZoneDefaultGoesToKitchen() {
        Warehouse kitchen = wh("KITCHEN");
        assertThat(ProductionZone.outputWarehouse(true, null, kitchen)).isSameAs(kitchen);
        assertThat(ProductionZone.outputWarehouse(true, wh("SHOP"), kitchen)).isSameAs(kitchen);
        assertThat(ProductionZone.outputWarehouse(true, wh("MAIN"), kitchen)).isSameAs(kitchen);
    }

    @Test void finishedProductAlwaysGoesToKitchen() {
        Warehouse kitchen = wh("KITCHEN");
        // thành phẩm chờ giao ra cửa hàng: không cất Phòng lạnh dù item có kho mặc định COLD
        assertThat(ProductionZone.outputWarehouse(false, wh("COLD"), kitchen)).isSameAs(kitchen);
    }
}
