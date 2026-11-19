package com.bakery.api.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.entity.PaymentStatus;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.ItemPackaging;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.InventoryRequestType;
import org.junit.jupiter.api.Test;

/** Phép tính giá, kiểm số nhận, trạng thái giao, điều kiện thanh toán của phiếu nhập — không cần Spring/DB. */
class InventoryRequestServiceTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private static ItemPackaging pack(String qtyPerPack) {
        ItemPackaging p = new ItemPackaging();
        p.setQtyPerPack(bd(qtyPerPack));
        return p;
    }

    private static InventoryRequestLine line(String name, String qty, String unitCost, ItemPackaging pack, String purchaseQty) {
        Ingredient item = new Ingredient();
        item.setName(name);
        InventoryRequestLine l = new InventoryRequestLine();
        l.setId(UUID.randomUUID());
        l.setItem(item);
        l.setQuantity(bd(qty));
        l.setUnit("KG");
        l.setUnitCost(unitCost == null ? null : bd(unitCost));
        l.setPackaging(pack);
        l.setPurchaseQty(purchaseQty == null ? null : bd(purchaseQty));
        return l;
    }

    // ── BE-1: giá theo item.unit ─────────────────────────────────

    @Test
    void unitCostPerCanonical_divides_pack_price_by_qtyPerPack() {
        var l = line("Bột", "20", "500000", pack("10"), "2");
        assertThat(InventoryRequestService.unitCostPerCanonical(l)).isEqualByComparingTo("50000");
    }

    @Test
    void unitCostPerCanonical_without_pack_keeps_price() {
        var l = line("Muối", "5", "40000", null, null);
        assertThat(InventoryRequestService.unitCostPerCanonical(l)).isEqualByComparingTo("40000");
    }

    @Test
    void unitCostPerCanonical_missing_cost_is_zero() {
        assertThat(InventoryRequestService.unitCostPerCanonical(line("X", "1", null, null, null)))
                .isEqualByComparingTo("0");
    }

    // ── BE-2: thành tiền ─────────────────────────────────────────

    @Test
    void lineTotal_approved_pack_multiplies_before_dividing() {
        // 2 bao × 10kg × 500.000đ/bao, nhận 15kg → 750.000
        var l = line("Bột", "20", "500000", pack("10"), "2");
        l.setReceivedQty(bd("15"));
        assertThat(InventoryRequestService.lineTotal(l, true)).isEqualByComparingTo("750000.00");
    }

    @Test
    void lineTotal_approved_pack_rounds_half_up_to_2_digits() {
        // 100000 × 1 ÷ 3 = 33333.333… → 33333.33 (chia trước rồi nhân sẽ lệch)
        var l = line("Bột", "3", "100000", pack("3"), "1");
        l.setReceivedQty(bd("1"));
        assertThat(InventoryRequestService.lineTotal(l, true)).isEqualByComparingTo("33333.33");
    }

    @Test
    void lineTotal_approved_without_pack_uses_receivedQty() {
        var l = line("Muối", "5", "40000", null, null);
        l.setReceivedQty(bd("4"));
        assertThat(InventoryRequestService.lineTotal(l, true)).isEqualByComparingTo("160000");
    }

    @Test
    void lineTotal_pending_uses_ordered_qty_not_received() {
        var withPack = line("Bột", "20", "500000", pack("10"), "2");
        assertThat(InventoryRequestService.lineTotal(withPack, false)).isEqualByComparingTo("1000000");
        // dòng không bao trước đây trả null
        var noPack = line("Muối", "5", "40000", null, null);
        assertThat(InventoryRequestService.lineTotal(noPack, false)).isEqualByComparingTo("200000");
    }

    @Test
    void lineTotal_null_without_cost() {
        assertThat(InventoryRequestService.lineTotal(line("X", "1", null, null, null), true)).isNull();
    }

    // ── BE-2: kiểm số nhận ───────────────────────────────────────

    @Test
    void validateReceived_accepts_short_delivery_and_zero_line() {
        var a = line("Bột", "20", "1", null, null);
        var b = line("Đường", "10", "1", null, null);
        InventoryRequestService.validateReceived(List.of(a, b),
                Map.of(a.getId(), bd("15"), b.getId(), bd("0")));
    }

    @Test
    void validateReceived_rejects_over_delivery_naming_the_item() {
        var a = line("Bột Tarta", "20", "1", null, null);
        assertThatThrownBy(() -> InventoryRequestService.validateReceived(List.of(a), Map.of(a.getId(), bd("21"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bột Tarta")
                .hasMessageContaining("từ chối hoặc sửa phiếu");
    }

    @Test
    void validateReceived_rejects_negative() {
        var a = line("Bột", "20", "1", null, null);
        assertThatThrownBy(() -> InventoryRequestService.validateReceived(List.of(a), Map.of(a.getId(), bd("-1"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Bột");
    }

    @Test
    void validateReceived_rejects_all_zero() {
        var a = line("Bột", "20", "1", null, null);
        assertThatThrownBy(() -> InventoryRequestService.validateReceived(List.of(a), Map.of(a.getId(), bd("0"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("từ chối phiếu");
    }

    // ── BE-4: tình trạng giao ────────────────────────────────────

    private static InventoryRequest req(InventoryRequestType t, ApprovalStatus s, LocalDate expected) {
        InventoryRequest r = new InventoryRequest();
        r.setRequestType(t);
        r.setApprovalStatus(s);
        r.setExpectedDeliveryDate(expected);
        return r;
    }

    @Test
    void deliveryStatus_matrix() {
        LocalDate today = LocalDate.of(2026, 11, 4);
        var P = InventoryRequestType.PURCHASE;
        assertThat(InventoryRequestService.deliveryStatus(req(P, ApprovalStatus.APPROVED, today.minusDays(9)), today)).isEqualTo("DELIVERED");
        assertThat(InventoryRequestService.deliveryStatus(req(P, ApprovalStatus.PENDING_APPROVAL, today), today)).isEqualTo("PENDING");
        assertThat(InventoryRequestService.deliveryStatus(req(P, ApprovalStatus.PENDING_APPROVAL, today.minusDays(1)), today)).isEqualTo("OVERDUE");
        assertThat(InventoryRequestService.deliveryStatus(req(P, ApprovalStatus.DRAFT, null), today)).isEqualTo("PENDING");
        assertThat(InventoryRequestService.deliveryStatus(req(P, ApprovalStatus.REJECTED, today), today)).isNull();
        assertThat(InventoryRequestService.deliveryStatus(
                req(InventoryRequestType.TRANSFER, ApprovalStatus.PENDING_APPROVAL, today), today)).isNull();
    }

    // ── BE-3: điều kiện thanh toán ───────────────────────────────

    private static InventoryRequest paid(ApprovalStatus s, PaymentStatus p, String unitCost) {
        InventoryRequest r = req(InventoryRequestType.PURCHASE, s, null);
        r.setPaymentStatus(p);
        r.getLines().add(line("Bột", "1", unitCost, null, null));
        return r;
    }

    @Test
    void paymentBlocker_only_approved_unpaid_purchase_with_prices() {
        assertThat(InventoryRequestService.paymentBlocker(paid(ApprovalStatus.APPROVED, PaymentStatus.UNPAID, "1"))).isNull();
        assertThat(InventoryRequestService.paymentBlocker(paid(ApprovalStatus.APPROVED, PaymentStatus.PAID, "1"))).isEqualTo("đã thanh toán");
        assertThat(InventoryRequestService.paymentBlocker(paid(ApprovalStatus.PENDING_APPROVAL, PaymentStatus.UNPAID, "1"))).isEqualTo("chưa xác nhận");
        assertThat(InventoryRequestService.paymentBlocker(paid(ApprovalStatus.APPROVED, PaymentStatus.UNPAID, null))).isEqualTo("thiếu đơn giá");
        assertThat(InventoryRequestService.paymentBlocker(
                req(InventoryRequestType.TRANSFER, ApprovalStatus.APPROVED, null))).isEqualTo("không phải phiếu nhập");
    }

    @Test void warehouseBlocker() {
        var P = com.bakery.framework.entity.InventoryRequestType.PURCHASE;
        var T = com.bakery.framework.entity.InventoryRequestType.TRANSFER;
        var A = com.bakery.framework.entity.InventoryRequestType.ADJUSTMENT;
        // thiếu kho nhận: mọi loại phiếu
        assertThat(InventoryRequestService.warehouseBlocker(P, false, false)).isEqualTo("Phiếu phải có kho nhận");
        assertThat(InventoryRequestService.warehouseBlocker(A, false, false)).isEqualTo("Phiếu phải có kho nhận");
        assertThat(InventoryRequestService.warehouseBlocker(T, true, false)).isEqualTo("Phiếu phải có kho nhận");
        // điều chuyển thiếu kho xuất
        assertThat(InventoryRequestService.warehouseBlocker(T, false, true)).isEqualTo("Phiếu điều chuyển phải có kho xuất");
        // đủ kho
        assertThat(InventoryRequestService.warehouseBlocker(P, false, true)).isNull();
        assertThat(InventoryRequestService.warehouseBlocker(A, false, true)).isNull();
        assertThat(InventoryRequestService.warehouseBlocker(T, true, true)).isNull();
    }
}
