package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.bakery.api.inventory.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

/** 15/BE-1: hủy thanh toán chỉ trong 30 ngày kể từ lúc thanh toán; mốc tính theo thời điểm, {@code now} truyền vào. */
class UnpayWindowTest {

    private final Instant now = Instant.parse("2026-11-17T03:00:00Z");

    private Instant paidDaysAgo(long days) { return now.minus(Duration.ofDays(days)); }

    @Test
    void within30DaysCanUnpay() {
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(0), now)).isTrue();
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(29), now)).isTrue();
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(30).plusSeconds(1), now)).isTrue();   // còn 1 giây
    }

    @Test
    void exactlyOrOver30DaysIsLocked() {
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(30), now)).isFalse();   // paidAt + 30 ngày = now → không sau now
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(31), now)).isFalse();
        assertThat(InventoryRequestService.withinUnpayWindow(paidDaysAgo(400), now)).isFalse();
    }

    @Test
    void missingPaidAtIsLocked() {
        assertThat(InventoryRequestService.withinUnpayWindow(null, now)).isFalse();
    }

    @Test
    void canUnpayNeedsAdminPaidAndInWindow() {
        assertThat(InventoryRequestService.canUnpay(true, PaymentStatus.PAID, paidDaysAgo(29), now)).isTrue();
        assertThat(InventoryRequestService.canUnpay(true, PaymentStatus.PAID, paidDaysAgo(31), now)).isFalse();
        assertThat(InventoryRequestService.canUnpay(false, PaymentStatus.PAID, paidDaysAgo(1), now)).isFalse();   // không phải Admin
        assertThat(InventoryRequestService.canUnpay(true, PaymentStatus.UNPAID, paidDaysAgo(1), now)).isFalse();
        assertThat(InventoryRequestService.canUnpay(true, null, null, now)).isFalse();                            // phiếu không phải PURCHASE
    }

    @Test
    void messageHasCodeAndPaymentDateInVietnamTime() {
        // 18:00 UTC ngày 01/10 = 01:00 ngày 02/10 giờ Việt Nam
        assertThat(InventoryRequestService.unpayExpiredMessage("PO-20261001-001", Instant.parse("2026-10-01T18:00:00Z")))
                .isEqualTo("Phiếu PO-20261001-001 đã thanh toán quá 30 ngày (ngày 02/10/2026), không hủy được");
        assertThat(InventoryRequestService.unpayExpiredMessage("PO-X", null)).contains("PO-X").contains("không rõ ngày");
    }
}
