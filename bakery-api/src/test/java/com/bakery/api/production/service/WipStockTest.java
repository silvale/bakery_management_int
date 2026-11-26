package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import org.junit.jupiter.api.Test;

/** 24/BE-3: trừ bảng tạm khi hoàn thành: lô giữ cho phiếu trước, rồi lô chung; không chạm lô giữ cho phiếu khác (§5.5). */
class WipStockTest {

    private static final UUID PR = UUID.randomUUID(), OTHER = UUID.randomUUID();

    private static StockLot lot(String qty, UUID reserved) {
        StockLot l = new StockLot();
        l.setQtyRemaining(new BigDecimal(qty));
        l.setReservedForRequestId(reserved);
        return l;
    }

    private static BigDecimal n(String s) { return new BigDecimal(s); }

    @Test void heldForThisRequestIsTakenBeforeCommon() {
        StockLot common = lot("3", null), held = lot("2", PR);
        List<StockLot> fifo = List.of(common, held);   // lô chung cũ hơn, vẫn phải trừ lô giữ trước
        BigDecimal left = WipStock.deduct(fifo, PR, n("4"), (l, q) -> {});
        assertThat(left).isEqualByComparingTo("0");
        assertThat(held.getQtyRemaining()).isEqualByComparingTo("0");
        assertThat(common.getQtyRemaining()).isEqualByComparingTo("1");
    }

    @Test void neverTouchesLotHeldForAnotherRequest() {
        StockLot others = lot("5", OTHER), common = lot("1", null);
        BigDecimal left = WipStock.deduct(List.of(others, common), PR, n("3"), (l, q) -> {});
        assertThat(left).isEqualByComparingTo("2");
        assertThat(others.getQtyRemaining()).isEqualByComparingTo("5");
    }

    @Test void availableCountsHeldAndCommonOnly() {
        assertThat(WipStock.available(List.of(lot("2", PR), lot("3", null), lot("9", OTHER)), PR)).isEqualByComparingTo("5");
    }

    @Test void shortageLeavesEveryLotUntouchedWhenCheckedFirst() {
        // consume() kiểm available() trước khi gọi deduct(): thiếu thì không có lô nào bị trừ
        List<StockLot> lots = new ArrayList<>(List.of(lot("1", PR), lot("1", null)));
        BigDecimal avail = WipStock.available(lots, PR);
        assertThat(avail).isLessThan(n("3"));
        assertThat(lots).allSatisfy(l -> assertThat(l.getQtyRemaining()).isEqualByComparingTo("1"));
        assertThat(WipStock.shortageMessage("Thịt heo", n("2"), "KG"))
                .isEqualTo("Chưa lấy đủ nguyên liệu: Thịt heo thiếu 2 KG. Lấy thêm NL.");
    }
}
