package com.bakery.api.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.bakery.api.inventory.entity.StockLot;
import org.junit.jupiter.api.Test;

class DailyReportServiceTest {

    private static StockLot lot(String qty) {
        StockLot l = new StockLot();
        l.setQtyRemaining(new BigDecimal(qty));
        return l;
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void deductsOldestLotFirstAndSpansLots() {
        StockLot a = lot("4"), b = lot("6");
        List<BigDecimal> taken = new ArrayList<>();
        BigDecimal shortfall = DailyReportService.deductFifo(List.of(a, b), bd("7"), (l, q) -> taken.add(q));
        assertThat(shortfall).isEqualByComparingTo("0");
        assertThat(a.getQtyRemaining()).isEqualByComparingTo("0");
        assertThat(b.getQtyRemaining()).isEqualByComparingTo("3");
        assertThat(taken).hasSize(2);
        assertThat(taken.get(0)).isEqualByComparingTo("4");
        assertThat(taken.get(1)).isEqualByComparingTo("3");
    }

    @Test
    void shortfallDrainsEverythingNeverNegative() {
        StockLot a = lot("7");
        BigDecimal shortfall = DailyReportService.deductFifo(List.of(a), bd("9"), (l, q) -> {});
        assertThat(a.getQtyRemaining()).isEqualByComparingTo("0");
        assertThat(shortfall).isEqualByComparingTo("2");
    }

    @Test
    void noLotsMeansWholeQtyShort() {
        assertThat(DailyReportService.deductFifo(List.of(), bd("3"), (l, q) -> {})).isEqualByComparingTo("3");
    }

    @Test
    void exactMatchLeavesNoShortfallAndNoExtraMovement() {
        StockLot a = lot("3");
        List<BigDecimal> taken = new ArrayList<>();
        assertThat(DailyReportService.deductFifo(List.of(a, lot("5")), bd("3"), (l, q) -> taken.add(q))).isEqualByComparingTo("0");
        assertThat(taken).hasSize(1);
    }
}
