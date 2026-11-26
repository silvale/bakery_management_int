package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.bakery.api.production.entity.ProductionSession;
import com.bakery.api.production.entity.SessionStatus;
import org.junit.jupiter.api.Test;

/** 24/BE-2, BE-4: quy tắc phiên ngày (§4.4) và kiểm dòng đóng phiên (§5.6). */
class ProductionSessionServiceTest {

    private static ProductionSession s(String date, SessionStatus st) {
        ProductionSession p = new ProductionSession();
        p.setSessionDate(LocalDate.parse(date));
        p.setSessionStatus(st);
        return p;
    }

    private static final LocalDate D = LocalDate.parse("2026-11-27");

    @Test void olderOpenSessionBlocksPicking() {
        assertThat(ProductionSessionService.sessionBlocker(List.of(s("2026-11-26", SessionStatus.OPEN)), D))
                .isEqualTo("Phiên ngày 26/11 chưa đóng, bếp trưởng đóng phiên trước");
    }

    @Test void closedSessionOfTheDayBlocksPicking() {
        assertThat(ProductionSessionService.sessionBlocker(List.of(s("2026-11-27", SessionStatus.CLOSED)), D))
                .isEqualTo("Phiên ngày 27/11 đã đóng");
    }

    @Test void firstPickOfTheDayOpensASession() {
        // không có phiên nào → không bị chặn, và sessionOn trả null để service mở phiên mới
        assertThat(ProductionSessionService.sessionBlocker(List.of(), D)).isNull();
        assertThat(ProductionSessionService.sessionOn(List.of(), D)).isNull();
        ProductionSession open = s("2026-11-27", SessionStatus.OPEN);
        assertThat(ProductionSessionService.sessionBlocker(List.of(open), D)).isNull();
        assertThat(ProductionSessionService.sessionOn(List.of(open), D)).isSameAs(open);
    }

    @Test void closeLineMustSumToRemaining() {
        BigDecimal rem = new BigDecimal("5");
        assertThat(ProductionSessionService.closeLineBlocker("Thịt", rem, new BigDecimal("2"), new BigDecimal("0.5"), new BigDecimal("2.5"))).isNull();
        assertThat(ProductionSessionService.closeLineBlocker("Thịt", rem, new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualTo("Thịt: trả + hao hụt + giữ (2) phải bằng phần còn (5)");
        assertThat(ProductionSessionService.closeLineBlocker("Thịt", rem, new BigDecimal("-1"), new BigDecimal("6"), BigDecimal.ZERO))
                .contains("không được âm");
    }
}
