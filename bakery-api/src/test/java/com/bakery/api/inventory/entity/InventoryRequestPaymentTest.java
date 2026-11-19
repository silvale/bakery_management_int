package com.bakery.api.inventory.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.framework.entity.InventoryRequestType;
import org.junit.jupiter.api.Test;

/** Phiếu PURCHASE luôn UNPAID khi lưu mới (kể cả phiếu generate-purchase không qua beforeCreate). */
class InventoryRequestPaymentTest {

    @Test
    void purchase_defaults_to_unpaid_on_persist() {
        InventoryRequest r = new InventoryRequest();
        r.setRequestType(InventoryRequestType.PURCHASE);
        r.initPaymentStatus();
        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void other_types_stay_null() {
        InventoryRequest r = new InventoryRequest();
        r.setRequestType(InventoryRequestType.TRANSFER);
        r.initPaymentStatus();
        assertThat(r.getPaymentStatus()).isNull();
    }

    @Test
    void persist_does_not_override_existing_status() {
        InventoryRequest r = new InventoryRequest();
        r.setRequestType(InventoryRequestType.PURCHASE);
        r.setPaymentStatus(PaymentStatus.PAID);
        r.initPaymentStatus();
        assertThat(r.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
