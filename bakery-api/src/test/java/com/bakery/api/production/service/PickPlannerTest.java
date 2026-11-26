package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.production.service.PickPlanner.Alloc;
import com.bakery.api.production.service.PickPlanner.Want;
import org.junit.jupiter.api.Test;

/** 24/BE-2: chia kho cho phiếu lấy tự tạo (§5.1). */
class PickPlannerTest {

    private static final UUID HOME = UUID.randomUUID(), COLD = UUID.randomUUID(), MAIN = UUID.randomUUID();
    private static final UUID MEAT = UUID.randomUUID(), CREAM = UUID.randomUUID();
    private static final List<UUID> ORDER = List.of(HOME, COLD, MAIN);

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    private static List<Alloc> run(Map<String, String> stock, Want... wants) {
        return PickPlanner.allocate(List.of(wants), ORDER, (w, i) -> n(stock.getOrDefault(w + "|" + i, "0")));
    }

    @Test void enoughAtHomeTakesAllFromHome() {
        var out = run(Map.of(HOME + "|" + MEAT, "10"), new Want(MEAT, n("4"), HOME));
        assertThat(out).containsExactly(new Alloc(HOME, MEAT, n("4"), false));
    }

    @Test void shortAtHomeSpillsToOtherSourcesInOrder() {
        var out = run(Map.of(HOME + "|" + MEAT, "1", COLD + "|" + MEAT, "2", MAIN + "|" + MEAT, "9"), new Want(MEAT, n("5"), HOME));
        assertThat(out).containsExactly(new Alloc(HOME, MEAT, n("1"), false), new Alloc(COLD, MEAT, n("2"), false), new Alloc(MAIN, MEAT, n("2"), false));
    }

    @Test void nothingEnoughLeavesShortageOnHomeLineMarkedStockShort() {
        var out = run(Map.of(HOME + "|" + MEAT, "1", COLD + "|" + MEAT, "1"), new Want(MEAT, n("5"), HOME));
        assertThat(out).containsExactly(new Alloc(HOME, MEAT, n("4"), true), new Alloc(COLD, MEAT, n("1"), false));
    }

    @Test void noStockAnywhereIsAllShortAtHome() {
        assertThat(run(Map.of(), new Want(CREAM, n("2.5"), HOME))).containsExactly(new Alloc(HOME, CREAM, n("2.5"), true));
    }

    @Test void semiProductUsesItsDefaultWarehouseAsHome() {
        var out = run(Map.of(COLD + "|" + CREAM, "8"), new Want(CREAM, n("3"), COLD));
        assertThat(out).containsExactly(new Alloc(COLD, CREAM, n("3"), false));
    }

    @Test void zeroNeedSkippedAndMissingHomeFallsBackToFirstSource() {
        assertThat(run(Map.of(), new Want(MEAT, BigDecimal.ZERO, HOME))).isEmpty();
        assertThat(run(Map.of(), new Want(MEAT, n("1"), null))).containsExactly(new Alloc(HOME, MEAT, n("1"), true));
        assertThatThrownBy(() -> PickPlanner.allocate(List.of(new Want(MEAT, n("1"), null)), List.of(), (w, i) -> BigDecimal.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
