package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.master.entity.Warehouse;
import org.junit.jupiter.api.Test;

/** 22/§5.2: trừ NL nhóm 1 (Phòng) trước, thiếu mới sang nhóm 2 (Kho Chính). */
class ProductionStockTest {

    private final Warehouse room = wh(), cold = wh(), main = wh();
    private final Set<UUID> primary = Set.of(room.getId(), cold.getId());
    private final Set<UUID> fallback = Set.of(main.getId());

    private static Warehouse wh() {
        Warehouse w = new Warehouse();
        w.setId(UUID.randomUUID());
        return w;
    }

    private static StockLot lot(Warehouse w, String qty, int day) {
        StockLot l = new StockLot();
        l.setWarehouse(w);
        l.setQtyRemaining(new BigDecimal(qty));
        l.setReceivedDate(LocalDate.of(2026, 1, day));
        return l;
    }

    private BigDecimal run(List<StockLot> lots, String need, List<String> log) {
        return ProductionStock.deductGroups(lots, primary, fallback, new BigDecimal(need),
                (l, taken) -> log.add((l.getWarehouse() == main ? "main" : l.getWarehouse() == room ? "room" : "cold") + ":" + taken.stripTrailingZeros().toPlainString()));
    }

    @Test void enoughInPrimaryNeverTouchesMain() {
        // lô Kho Chính cũ nhất (ngày 1) vẫn không bị trừ khi phòng đủ
        List<StockLot> lots = List.of(lot(main, "100", 1), lot(room, "4", 2), lot(cold, "6", 3));
        List<String> log = new ArrayList<>();
        assertThat(run(lots, "8", log)).isEqualByComparingTo("0");
        assertThat(log).containsExactly("room:4", "cold:4");
        assertThat(lots.get(0).getQtyRemaining()).isEqualByComparingTo("100");
    }

    @Test void shortInPrimaryTakesRestFromMain() {
        List<StockLot> lots = List.of(lot(main, "100", 1), lot(room, "4", 2));
        List<String> log = new ArrayList<>();
        assertThat(run(lots, "10", log)).isEqualByComparingTo("0");
        assertThat(log).containsExactly("room:4", "main:6");
        assertThat(lots.get(0).getQtyRemaining()).isEqualByComparingTo("94");
    }

    @Test void shortEverywhereReturnsRemainderAndNeverGoesNegative() {
        List<StockLot> lots = List.of(lot(main, "3", 1), lot(room, "4", 2));
        List<String> log = new ArrayList<>();
        assertThat(run(lots, "10", log)).isEqualByComparingTo("3");
        assertThat(lots).allSatisfy(l -> assertThat(l.getQtyRemaining()).isEqualByComparingTo("0"));
    }

    @Test void lotsOutsideBothGroupsAreIgnored() {
        Warehouse other = wh();
        List<StockLot> lots = List.of(lot(other, "50", 1));
        assertThat(run(lots, "5", new ArrayList<>())).isEqualByComparingTo("5");
        assertThat(lots.get(0).getQtyRemaining()).isEqualByComparingTo("50");
    }
}
