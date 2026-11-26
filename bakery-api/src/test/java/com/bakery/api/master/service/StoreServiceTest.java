package com.bakery.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakery.api.master.dto.StoreRequest;
import com.bakery.api.master.entity.Store;
import org.junit.jupiter.api.Test;

/** 23/BE-1: quy tắc sửa/xóa cửa hàng, hàm tĩnh nên không cần Spring. */
class StoreServiceTest {

    private static Store store(String code) {
        Store s = new Store();
        s.setCode(code);
        return s;
    }

    @Test void codeCannotChange() {
        assertThat(StoreService.codeBlocker(store("CH1"), new StoreRequest("CH9", "x", null))).isEqualTo("Không đổi được mã cửa hàng");
        assertThat(StoreService.codeBlocker(store("CH1"), new StoreRequest("CH1", "đổi tên", 5))).isNull();
    }

    @Test void cannotDeleteStoreThatStillHasWarehouses() {
        assertThat(StoreService.deleteBlocker(3)).isEqualTo("Cửa hàng còn 3 kho, chuyển kho sang cửa hàng khác trước khi xóa");
        assertThat(StoreService.deleteBlocker(0)).isNull();
    }
}
