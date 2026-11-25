package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ItemGroupServiceTest {
    @Test
    void deleteBlocker() {
        assertThat(ItemGroupService.deleteBlocker(0, 0, 0)).isNull();
        assertThat(ItemGroupService.deleteBlocker(3, 0, 0)).isEqualTo("Nhóm đang có 3 mặt hàng, chuyển mặt hàng sang nhóm khác trước khi xóa");
        assertThat(ItemGroupService.deleteBlocker(0, 2, 0)).contains("2 mặt hàng đã xóa");
        assertThat(ItemGroupService.deleteBlocker(0, 0, 1)).contains("1 nhóm kế hoạch sản xuất");
        assertThat(ItemGroupService.deleteBlocker(1, 1, 1)).contains("1 mặt hàng,").contains("đã xóa").contains("kế hoạch");
    }
}
