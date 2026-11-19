package com.bakery.api.cake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.cake.dto.DesignQtyRequest;
import org.junit.jupiter.api.Test;

class DesignSplitTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), other = UUID.randomUUID();
    private final Set<UUID> allowed = Set.of(a, b);

    private static DesignQtyRequest d(UUID id, String qty) { return new DesignQtyRequest(id, new BigDecimal(qty)); }

    @Test
    void validSplitReturnsOnlyPositiveQuantities() {
        var m = DesignSplit.validate(List.of(d(a, "2"), d(b, "1"), d(other, "0")),
                Set.of(a, b, other), new BigDecimal("3"), "làm ra");
        assertThat(m).containsOnlyKeys(a, b);
        assertThat(m.get(a)).isEqualByComparingTo("2");
    }

    @Test
    void zeroTotalIgnoresDesigns() {
        assertThat(DesignSplit.validate(null, allowed, BigDecimal.ZERO, "làm ra")).isEmpty();
    }

    @Test
    void missingDesignsWhenQtyPositive() {
        assertThatThrownBy(() -> DesignSplit.validate(List.of(), allowed, new BigDecimal("3"), "làm ra"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Vui lòng chọn mẫu bánh");
        assertThatThrownBy(() -> DesignSplit.validate(null, allowed, new BigDecimal("3"), "làm ra"))
                .hasMessage("Vui lòng chọn mẫu bánh");
    }

    @Test
    void totalMismatchMessage() {
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(a, "1"), d(b, "1")), allowed, new BigDecimal("3"), "làm ra"))
                .hasMessage("Tổng số mẫu (2) khác số lượng làm ra (3)");
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(a, "1")), allowed, new BigDecimal("3"), "thực nhận"))
                .hasMessage("Tổng số mẫu (1) khác số lượng thực nhận (3)");
    }

    @Test
    void designOfAnotherProductRejected() {
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(other, "3")), allowed, new BigDecimal("3"), "làm ra"))
                .hasMessageContaining("không thuộc thành phẩm");
    }

    @Test
    void negativeOrFractionalQtyRejected() {
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(a, "-1"), d(b, "4")), allowed, new BigDecimal("3"), "làm ra"))
                .hasMessage("Số lượng mẫu phải là số nguyên không âm");
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(a, "1.5"), d(b, "1.5")), allowed, new BigDecimal("3"), "làm ra"))
                .hasMessage("Số lượng mẫu phải là số nguyên không âm");
        // 2.0 vẫn là số nguyên
        assertThat(DesignSplit.validate(List.of(d(a, "2.0"), d(b, "1")), allowed, new BigDecimal("3"), "làm ra")).hasSize(2);
    }

    @Test
    void duplicateDesignRejected() {
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(a, "1"), d(a, "2")), allowed, new BigDecimal("3"), "làm ra"))
                .hasMessage("Mẫu bị chọn trùng");
    }

    @Test
    void errorMessageShowsDesignCodeOrNotFound() {   // 02/BE-10
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(other, "3")), allowed, new BigDecimal("3"), "làm ra", id -> "SN20"))
                .hasMessage("Mẫu SN20 không thuộc thành phẩm của dòng này hoặc đã ngừng dùng");
        assertThatThrownBy(() -> DesignSplit.validate(List.of(d(other, "3")), allowed, new BigDecimal("3"), "làm ra", id -> null))
                .hasMessage("Không tìm thấy mẫu " + other);
    }
}
