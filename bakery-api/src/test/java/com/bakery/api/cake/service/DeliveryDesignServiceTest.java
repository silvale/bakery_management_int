package com.bakery.api.cake.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.DeliveryRecordDesign;
import org.junit.jupiter.api.Test;

class DeliveryDesignServiceTest {

    private static DeliveryRecordDesign row(String code, String produced, String received) {
        CakeDesign d = new CakeDesign();
        d.setCode(code);
        DeliveryRecordDesign r = new DeliveryRecordDesign();
        r.setCakeDesign(d);
        r.setQtyProduced(new BigDecimal(produced));
        r.setQtyReceived(received == null ? null : new BigDecimal(received));
        return r;
    }

    @Test
    void noMismatchWhenAllEqual() {
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "2", "2"), row("XD16", "1", "1")))).isFalse();
    }

    @Test
    void mismatchOnlyWhenShopReceivedMoreThanKitchenDeclared() {
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "2", "1"), row("XD16", "1", "2")))).isTrue();
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "3", "3"), row("BG16", "0", "1")))).isTrue();
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "3", "4")))).isTrue();   // nhận nhiều hơn khai
    }

    @Test
    void receivingLessIsNotMismatch() {   // D5/D11: hỏng hoặc điều chỉnh ≠ sai mẫu
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "3", "2")))).isFalse();
        assertThat(DeliveryDesignService.isMismatch(List.of(row("SN16", "2", "2"), row("CC16", "1", "0")))).isFalse();
    }

    @Test
    void mismatchTextMatchesSpecFormatSortedByCode() {
        assertThat(DeliveryDesignService.mismatchText(List.of(row("XD16", "1", "2"), row("SN16", "2", "1"))))
                .isEqualTo("bếp SN16×2, XD16×1; cửa hàng SN16×1, XD16×2");
    }

    @Test
    void mismatchTextShowsDashForEmptySide() {
        assertThat(DeliveryDesignService.mismatchText(List.of(row("SN16", "2", "0"))))
                .isEqualTo("bếp SN16×2; cửa hàng —");
    }
}
