package com.bakery.api.master.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LowStockControllerTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test void restockMinusCurrent() {
        // ngưỡng 5, mức nhập 10, tồn 2 → 8
        assertThat(LowStockController.suggestedQty(d("10"), d("2"), d("3"))).isEqualByComparingTo("8");
        // hộp pento: ngưỡng 50, mức nhập 400, tồn 0 → 400
        assertThat(LowStockController.suggestedQty(d("400"), d("0"), d("50"))).isEqualByComparingTo("400");
    }

    @Test void noRestockFallsBackToShortage() {
        assertThat(LowStockController.suggestedQty(null, d("2"), d("3"))).isEqualByComparingTo("3");
        assertThat(LowStockController.suggestedQty(d("0"), d("2"), d("3"))).isEqualByComparingTo("3");
    }

    @Test void restockBelowCurrentFallsBackToShortage() {
        assertThat(LowStockController.suggestedQty(d("4"), d("4.5"), d("0.5"))).isEqualByComparingTo("0.5");
    }
}
