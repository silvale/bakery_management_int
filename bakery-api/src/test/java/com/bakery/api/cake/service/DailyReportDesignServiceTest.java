package com.bakery.api.cake.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DailyReportDesignServiceTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void soldIsOpeningPlusReceivedMinusRemaining() {
        // ví dụ spec: SN16 mở 1, nhận 2, còn 1 → bán 2; BG16 mở 2, nhận 1, còn 2 → bán 1
        assertThat(DailyReportDesignService.soldImplied(bd("1"), bd("2"), bd("1"), null)).isEqualByComparingTo("2");
        assertThat(DailyReportDesignService.soldImplied(bd("2"), bd("1"), bd("2"), bd("0"))).isEqualByComparingTo("1");
    }

    @Test
    void soldIsNullUntilRemainingEntered() {
        assertThat(DailyReportDesignService.soldImplied(bd("1"), bd("2"), null, bd("1"))).isNull();
    }

    @Test
    void nullOpeningOrReceivedCountAsZero() {
        assertThat(DailyReportDesignService.soldImplied(null, bd("3"), bd("1"), null)).isEqualByComparingTo("2");
    }

    @Test
    void cancelledIsLostNotSold() {   // D7 mới (02/BE-11)
        // XD16: mở 1, nhận 0, còn 0, hủy 1 → bán 0
        assertThat(DailyReportDesignService.soldImplied(bd("1"), bd("0"), bd("0"), bd("1"))).isEqualByComparingTo("0");
        // Bánh mì: mở 2, còn 1, hủy 1 → bán 0
        assertThat(DailyReportDesignService.soldImplied(bd("2"), null, bd("1"), bd("1"))).isEqualByComparingTo("0");
    }
}
