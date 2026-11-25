package com.bakery.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseKind;
import com.bakery.api.master.entity.WarehouseRole;
import org.junit.jupiter.api.Test;

/** 22/BE-1: nhóm 1 / nhóm 2 theo cấu hình §4.2. */
class WarehouseRolesTest {

    static Warehouse wh(String code, int sort, boolean receive, boolean prod, boolean semi, boolean hold, boolean sales, boolean export) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(code);
        w.setSortOrder(sort);
        w.setCanReceivePurchase(receive);
        w.setProductionSource(prod);
        w.setCanStoreSemi(semi);
        w.setDeliveryHold(hold);
        w.setSalesPoint(sales);
        w.setCanExportExternal(export);
        return w;
    }

    /** Thiết kế §4.2: Kho Chính, Phòng Bánh Mì, Phòng Lạnh, Bán Hàng, Kho Tổng (admin tạo), Nhà Hàng (ngoài). */
    static List<Warehouse> config() {
        Warehouse restaurant = wh("NH", 70, false, false, false, false, false, false);
        restaurant.setKind(WarehouseKind.EXTERNAL);
        return List.of(
                wh("SHOP", 50, false, false, false, false, true, false),
                wh("COLD", 40, false, true, true, false, false, false),
                wh("KHO", 10, true, false, false, false, false, true),     // Kho Tổng: nhận NCC, không sản xuất lấy NL
                wh("KITCHEN", 30, false, true, true, true, false, false),
                wh("MAIN", 20, true, true, false, false, false, true),
                restaurant);
    }

    private static List<String> codes(List<Warehouse> ws) { return ws.stream().map(Warehouse::getCode).toList(); }

    @Test void primaryIsProductionSourceWithoutReceive() {
        assertThat(codes(WarehouseRoles.primary(config()))).containsExactly("KITCHEN", "COLD");
    }

    @Test void fallbackIsProductionSourceThatReceivesPurchase() {
        assertThat(codes(WarehouseRoles.fallback(config()))).containsExactly("MAIN");   // Kho Tổng không sản xuất lấy NL
    }

    @Test void withRoleSortsBySortOrderThenCode() {
        assertThat(codes(WarehouseRoles.filter(config(), WarehouseRole.RECEIVE_PURCHASE))).containsExactly("KHO", "MAIN");
        assertThat(codes(WarehouseRoles.filter(config(), WarehouseRole.EXPORT_EXTERNAL))).containsExactly("KHO", "MAIN");
        assertThat(codes(WarehouseRoles.filter(config(), WarehouseRole.SALES_POINT))).containsExactly("SHOP");
        assertThat(codes(WarehouseRoles.filter(config(), WarehouseRole.DELIVERY_HOLD))).containsExactly("KITCHEN");
        assertThat(codes(WarehouseRoles.filter(config(), WarehouseRole.STORE_SEMI))).containsExactly("KITCHEN", "COLD");
    }

    @Test void externalWarehouseNeverHasARole() {
        Warehouse ext = wh("X", 1, true, true, true, true, true, true);
        ext.setKind(WarehouseKind.EXTERNAL);   // dù lỡ cờ bật (DB đã CHECK), đọc vẫn bỏ qua
        assertThat(WarehouseRoles.filter(List.of(ext), WarehouseRole.RECEIVE_PURCHASE)).isEmpty();
    }
}
