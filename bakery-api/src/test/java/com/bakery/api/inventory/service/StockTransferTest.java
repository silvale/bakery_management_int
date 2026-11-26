package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import org.junit.jupiter.api.Test;

/** 24/BE-1: RETURN chỉ trừ đúng nhóm lô (§4.3); TRANSFER (nhóm null) không đổi hành vi. */
class StockTransferTest {

    private static StockLot lot(UUID reserved) {
        StockLot l = new StockLot();
        l.setQtyRemaining(BigDecimal.ONE);
        l.setReservedForRequestId(reserved);
        return l;
    }

    @Test void commonGroupSkipsLotsHeldForAnyRequest() {
        UUID pr = UUID.randomUUID();
        StockLot common = lot(null), held = lot(pr);
        assertThat(StockTransfer.eligible(List.of(held, common), null)).containsExactly(common);
    }

    @Test void heldGroupTakesOnlyThatRequestsLots() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        StockLot la = lot(a), lb = lot(b), common = lot(null);
        assertThat(StockTransfer.eligible(List.of(la, lb, common), a)).containsExactly(la);
    }
}
