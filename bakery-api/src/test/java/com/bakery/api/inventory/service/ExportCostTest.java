package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import org.junit.jupiter.api.Test;

/** 25/BE-1: tiền dòng khi trừ qua nhiều lô khác giá và ẩn giá theo NCC của lô. */
class ExportCostTest {

    private static StockLot lot(String qty, String cost) {
        StockLot l = new StockLot();
        l.setQtyRemaining(new BigDecimal(qty));
        l.setUnitCost(new BigDecimal(cost));
        return l;
    }

    @Test void amountSumsEachLotAtItsOwnPurchasePrice() {
        StockLot a = lot("3", "40000"), b = lot("10", "50000");
        List<StockLot> hit = new ArrayList<>();
        ExportCost.Result r = ExportCost.deduct(List.of(a, b), new BigDecimal("5"), (l, q) -> hit.add(l));
        assertThat(r.amount()).isEqualByComparingTo("220000");        // 3 × 40000 + 2 × 50000
        assertThat(r.remaining()).isEqualByComparingTo("0");
        assertThat(hit).containsExactly(a, b);                        // mỗi lô một lần trừ
        assertThat(a.getQtyRemaining()).isEqualByComparingTo("0");
        assertThat(b.getQtyRemaining()).isEqualByComparingTo("8");
        assertThat(ExportCost.unitCost(r.amount(), new BigDecimal("5"))).isEqualByComparingTo("44000");
    }

    @Test void shortageIsReportedAndAmountCoversWhatWasTaken() {
        ExportCost.Result r = ExportCost.deduct(List.of(lot("2", "100")), new BigDecimal("5"), (l, q) -> {});
        assertThat(r.remaining()).isEqualByComparingTo("3");
        assertThat(r.amount()).isEqualByComparingTo("200");
        assertThat(ExportCost.unitCost(r.amount(), BigDecimal.ZERO)).isNull();
    }

    @Test void priceVisibleOnlyWhenEverySupplierOfTheLineIsVisible() {
        UUID ok = UUID.randomUUID(), hidden = UUID.randomUUID();
        java.util.function.Predicate<UUID> sees = id -> ok.equals(id);          // user chỉ xem được giá NCC "ok"
        assertThat(ExportCost.priceVisible(Set.of(ok), sees)).isTrue();
        assertThat(ExportCost.priceVisible(Set.of(ok, hidden), sees)).isFalse(); // một NCC được xem + một không → ẩn
        assertThat(ExportCost.priceVisible(new java.util.HashSet<>(Arrays.asList(ok, null)), sees)).isFalse(); // lô không NCC → chỉ Admin
        assertThat(ExportCost.priceVisible(new java.util.HashSet<>(Arrays.asList(ok, null)), id -> true)).isTrue(); // Admin xem hết
        assertThat(ExportCost.priceVisible(null, sees)).isFalse();
    }
}
