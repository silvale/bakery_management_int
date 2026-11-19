package com.bakery.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.WarehouseType;
import org.junit.jupiter.api.Test;

class WarehouseServiceTest {

    private static Warehouse wh(String code, WarehouseType type) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName("x");
        w.setWarehouseType(type);
        return w;
    }

    @Test void defaultWarehouseCannotChangeCode() {
        var req = new WarehouseRequest("SHOP2", "Cửa Hàng", WarehouseType.SHOP, null);
        assertThat(WarehouseService.updateBlocker(wh("SHOP", WarehouseType.SHOP), req)).contains("SHOP");
    }

    @Test void defaultWarehouseCannotChangeType() {
        var req = new WarehouseRequest("SHOP", "Cửa Hàng", WarehouseType.MAIN, null);
        assertThat(WarehouseService.updateBlocker(wh("SHOP", WarehouseType.SHOP), req)).isNotNull();
    }

    @Test void defaultWarehouseCanRename() {
        var req = new WarehouseRequest("SHOP", "Cửa Hàng Trung Tâm", WarehouseType.SHOP, "12 Lê Lợi");
        assertThat(WarehouseService.updateBlocker(wh("SHOP", WarehouseType.SHOP), req)).isNull();
    }

    @Test void customWarehouseIsFree() {
        var req = new WarehouseRequest("KHO9", "Kho 9", WarehouseType.MAIN, null);
        assertThat(WarehouseService.updateBlocker(wh("KHO2", WarehouseType.SHOP), req)).isNull();
    }

    @Test void fourDefaultCodes() {
        assertThat(WarehouseService.DEFAULT_CODES).containsExactlyInAnyOrder("MAIN", "KITCHEN", "SHOP", "COLD");
    }
}
