package com.bakery.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseKind;
import com.bakery.api.master.service.WarehouseService.Config;
import com.bakery.api.master.service.WarehouseService.Facts;
import org.junit.jupiter.api.Test;

/** 22/BE-1: quy tắc sửa/xóa kho (thiết kế §5.3), hàm tĩnh nên không cần Spring. */
class WarehouseServiceTest {

    private static final Facts NONE = new Facts(false, 0, 0, true, true);

    private static Warehouse wh(String code) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(code);
        return w;
    }

    private static WarehouseRequest req(String code, WarehouseKind kind, Boolean receive, Boolean prod, Boolean semi,
                                        Boolean hold, Boolean sales, Boolean export) {
        return new WarehouseRequest(code, "x", null, kind, receive, prod, semi, hold, sales, export, null, null, null);
    }

    private static WarehouseRequest sameCode(String code) { return req(code, null, null, null, null, null, null, null); }

    @Test void codeCannotChangeForAnyWarehouse() {
        assertThat(WarehouseService.updateBlocker(wh("MAIN"), sameCode("MAIN2"), NONE)).isEqualTo("Không đổi được mã kho");
        assertThat(WarehouseService.updateBlocker(wh("KHO9"), sameCode("KHO10"), NONE)).isEqualTo("Không đổi được mã kho");   // kho tự tạo cũng vậy
        assertThat(WarehouseService.updateBlocker(wh("MAIN"), sameCode("MAIN"), NONE)).isNull();
    }

    @Test void cannotTurnOffSalesPointInPlace() {
        Warehouse shop = wh("SHOP");
        shop.setSalesPoint(true);
        String m = WarehouseService.updateBlocker(shop, req("SHOP", null, null, null, null, null, false, null), NONE);
        assertThat(m).contains("Bán hàng").contains("chuyển");
        shop.setDeliveryHold(true);
        assertThat(WarehouseService.updateBlocker(shop, req("SHOP", null, null, null, null, false, null, null), NONE)).contains("Bánh chờ giao");
    }

    @Test void turningOnSalesPointAtAnotherWarehouseIsAllowedByRules() {
        Warehouse b = wh("B");   // B chưa là kho bán hàng; bật cờ hợp lệ (service tắt ở kho cũ cùng transaction)
        assertThat(WarehouseService.updateBlocker(b, req("B", null, null, null, null, null, true, null), NONE)).isNull();
    }

    @Test void cannotTurnOffStoreSemiWhileSemiItemsPointHere() {
        Warehouse w = wh("COLD");
        w.setCanStoreSemi(true);
        String m = WarehouseService.updateBlocker(w, req("COLD", null, null, null, false, null, null, null), new Facts(false, 0, 3, true, true));
        assertThat(m).contains("3").contains("bán thành phẩm");
        assertThat(WarehouseService.updateBlocker(w, req("COLD", null, null, null, false, null, null, null), NONE)).isNull();
    }

    @Test void cannotTurnOffProductionSourceWhileIngredientsPointHere() {
        Warehouse w = wh("COLD");
        w.setProductionSource(true);
        String m = WarehouseService.updateBlocker(w, req("COLD", null, null, false, null, null, null, null), new Facts(false, 5, 0, true, true));
        assertThat(m).contains("5").contains("nguyên liệu");
    }

    @Test void mustKeepAtLeastOneProductionSourceAndOneReceivePurchase() {
        Warehouse w = wh("MAIN");
        w.setProductionSource(true);
        w.setCanReceivePurchase(true);
        assertThat(WarehouseService.updateBlocker(w, req("MAIN", null, null, false, null, null, null, null), new Facts(false, 0, 0, false, true)))
                .isEqualTo("Phải còn ít nhất một kho Sản xuất lấy NL");
        assertThat(WarehouseService.updateBlocker(w, req("MAIN", null, false, null, null, null, null, null), new Facts(false, 0, 0, true, false)))
                .isEqualTo("Phải còn ít nhất một kho Nhận NCC");
    }

    @Test void kindCannotChangeOnceWarehouseHasStockOrRequests() {
        Warehouse w = wh("K");
        assertThat(WarehouseService.updateBlocker(w, req("K", WarehouseKind.EXTERNAL, null, null, null, null, null, null), new Facts(true, 0, 0, true, true)))
                .contains("loại kho");
        assertThat(WarehouseService.updateBlocker(w, req("K", WarehouseKind.EXTERNAL, null, null, null, null, null, null), NONE)).isNull();   // kho trống đổi được
    }

    @Test void externalWarehouseCannotEnableAnyRole() {
        Warehouse w = wh("NH");
        w.setKind(WarehouseKind.EXTERNAL);
        assertThat(WarehouseService.updateBlocker(w, req("NH", null, true, null, null, null, null, null), NONE)).contains("nơi nhận ngoài");
        assertThat(WarehouseService.externalBlocker(Config.apply(Config.of(wh("A")), req("A", WarehouseKind.EXTERNAL, null, null, null, null, true, null))))
                .isNotNull();
    }

    @Test void nullFlagsKeepCurrentConfig() {
        Warehouse w = wh("MAIN");
        w.setCanReceivePurchase(true);
        w.setProductionSource(true);
        Config after = Config.apply(Config.of(w), sameCode("MAIN"));
        assertThat(after.receive()).isTrue();
        assertThat(after.production()).isTrue();
        assertThat(after.sales()).isFalse();
    }

    @Test void deleteBlockers() {
        Config plain = Config.of(wh("X"));
        assertThat(WarehouseService.deleteBlocker(plain, 2, 0, true, true)).contains("2 lô tồn");
        assertThat(WarehouseService.deleteBlocker(plain, 0, 4, true, true)).contains("4 mặt hàng");
        Warehouse shop = wh("SHOP");
        shop.setSalesPoint(true);
        assertThat(WarehouseService.deleteBlocker(Config.of(shop), 0, 0, true, true)).contains("Bán hàng");
        Warehouse hold = wh("K");
        hold.setDeliveryHold(true);
        assertThat(WarehouseService.deleteBlocker(Config.of(hold), 0, 0, true, true)).contains("Bánh chờ giao");
        Warehouse lastSource = wh("S");
        lastSource.setProductionSource(true);
        assertThat(WarehouseService.deleteBlocker(Config.of(lastSource), 0, 0, false, true)).contains("Sản xuất lấy NL");
        assertThat(WarehouseService.deleteBlocker(plain, 0, 0, true, true)).isNull();
    }

    // ── Chuyển việc Bán hàng / Bánh chờ giao giữa hai kho (cùng transaction) ──

    @SuppressWarnings("unchecked")
    private static WarehouseService serviceOver(java.util.List<Warehouse> all, java.util.List<Warehouse> flushed) {
        var repo = (com.bakery.api.master.repository.WarehouseRepository) java.lang.reflect.Proxy.newProxyInstance(
                WarehouseServiceTest.class.getClassLoader(), new Class<?>[] {com.bakery.api.master.repository.WarehouseRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findAll" -> all;
                    case "saveAndFlush" -> { flushed.add((Warehouse) a[0]); yield a[0]; }
                    default -> null;
                });
        return new WarehouseService(repo, null, null, null, null);
    }

    @Test void movingSalesPointTurnsOffOldWarehouseAndLeavesNewOneOn() {
        Warehouse shop = wh("SHOP"), b = wh("B");
        shop.setId(java.util.UUID.randomUUID());
        b.setId(java.util.UUID.randomUUID());
        shop.setSalesPoint(true);
        var flushed = new java.util.ArrayList<Warehouse>();
        WarehouseService svc = serviceOver(java.util.List.of(shop, b), flushed);
        svc.transferSingletons(b, Config.apply(Config.of(b), req("B", null, null, null, null, null, true, null)));
        assertThat(shop.isSalesPoint()).isFalse();      // kho cũ tắt
        assertThat(flushed).containsExactly(shop);      // flush trước khi kho mới bật (unique index)
        assertThat(b.isSalesPoint()).isFalse();         // cờ của kho mới do applyUpdate gán sau, không bị transfer đụng tới
    }

    @Test void movingDeliveryHoldDoesNotTouchSalesPoint() {
        Warehouse shop = wh("SHOP"), k = wh("K"), c = wh("C");
        for (Warehouse w : java.util.List.of(shop, k, c)) w.setId(java.util.UUID.randomUUID());
        shop.setSalesPoint(true);
        k.setDeliveryHold(true);
        WarehouseService svc = serviceOver(java.util.List.of(shop, k, c), new java.util.ArrayList<>());
        svc.transferSingletons(c, Config.apply(Config.of(c), req("C", null, null, null, null, true, null, null)));
        assertThat(k.isDeliveryHold()).isFalse();
        assertThat(shop.isSalesPoint()).isTrue();
    }

    @Test void noTransferWhenTargetAlreadyHoldsTheRole() {
        Warehouse shop = wh("SHOP");
        shop.setId(java.util.UUID.randomUUID());
        shop.setSalesPoint(true);
        var flushed = new java.util.ArrayList<Warehouse>();
        serviceOver(java.util.List.of(shop), flushed).transferSingletons(shop, Config.of(shop));
        assertThat(flushed).isEmpty();
        assertThat(shop.isSalesPoint()).isTrue();
    }

    @Test void externalWarehouseCannotBelongToStore() {   // 23/BE-1
        assertThat(WarehouseService.storeBlocker(WarehouseKind.EXTERNAL, java.util.UUID.randomUUID()))
                .isEqualTo("Nơi nhận ngoài không thuộc cửa hàng");
        assertThat(WarehouseService.storeBlocker(WarehouseKind.EXTERNAL, null)).isNull();
        assertThat(WarehouseService.storeBlocker(WarehouseKind.INTERNAL, java.util.UUID.randomUUID())).isNull();
    }

    // ── 24/BE-1: kho bảng tạm (§4.1) ────────────────────────────────────────

    private static Config wipOnly() { return new Config(WarehouseKind.INTERNAL, false, false, false, false, false, false, true); }

    @Test void wipWarehouseMustBelongToAStore() {
        assertThat(WarehouseService.wipBlocker(wipOnly(), null)).isEqualTo("Kho bảng tạm phải thuộc một cửa hàng");
        assertThat(WarehouseService.wipBlocker(wipOnly(), java.util.UUID.randomUUID())).isNull();
        assertThat(WarehouseService.wipBlocker(new Config(WarehouseKind.INTERNAL, true, false, false, false, false, false, false), null)).isNull();   // kho thường không bị ràng buộc
    }

    @Test void wipWarehouseCannotEnableAnyOtherRole() {
        Config withSales = new Config(WarehouseKind.INTERNAL, false, false, false, false, true, false, true);
        assertThat(WarehouseService.wipBlocker(withSales, java.util.UUID.randomUUID())).isEqualTo("Kho bảng tạm không được bật việc nào khác");
    }

    @Test void oneWipPerStoreAndCannotTurnOffWithStock() {
        Config off = new Config(WarehouseKind.INTERNAL, false, false, false, false, false, false, false);
        assertThat(WarehouseService.wipChangeBlocker(off, wipOnly(), true, 0)).isEqualTo("Cửa hàng này đã có kho bảng tạm");
        assertThat(WarehouseService.wipChangeBlocker(off, wipOnly(), false, 0)).isNull();
        assertThat(WarehouseService.wipChangeBlocker(wipOnly(), off, false, 3)).contains("kho còn 3 lô tồn");
    }

    @Test void exportTargetsOnlyForAdminOrViewersAssignedToAnExportWarehouse() {   // 25/BE-1
        assertThat(WarehouseService.canSeeExportTargets(true, false, false)).isTrue();
        assertThat(WarehouseService.canSeeExportTargets(false, true, true)).isTrue();
        assertThat(WarehouseService.canSeeExportTargets(false, true, false)).isFalse();    // có quyền xem nhưng không gán kho xuất ngoài
        assertThat(WarehouseService.canSeeExportTargets(false, false, true)).isFalse();    // gán kho nhưng không có INVENTORY_REQUESTS:VIEW
    }
}
