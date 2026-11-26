package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.exception.EntityNames;
import com.bakery.framework.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

/** 26/BE-1: câu lỗi trả cho người dùng bằng tiếng Việt, không lộ tên field / enum / UUID. */
class VietnameseMessagesTest {

    @Test void varianceMessageUsesPlainNumbersAndNoFieldNames() {
        String m = ProductionRequestService.varianceMessage(new BigDecimal("9.0000"), new BigDecimal("10"));
        assertThat(m).isEqualTo("Số làm ra (9) khác kế hoạch (10). Chọn lý do lệch.");
        assertThat(ProductionRequestService.varianceMessage(new BigDecimal("2.5000"), new BigDecimal("3.00")))
                .isEqualTo("Số làm ra (2.5) khác kế hoạch (3). Chọn lý do lệch.");
        assertThat(m).doesNotContain("qtyProduced").doesNotContain("adjustmentType").doesNotContain("plannedQty");
    }

    @Test void notFoundNamesTheEntityInVietnameseWithoutUuid() {
        UUID id = UUID.randomUUID();
        assertThat(new ResourceNotFoundException("InventoryRequest", id).getMessage()).isEqualTo("Không tìm thấy phiếu kho");
        assertThat(new ResourceNotFoundException("SemiProduct", id).getMessage()).isEqualTo("Không tìm thấy hàng gia công");
        assertThat(new ResourceNotFoundException("Whatever", id).getMessage()).isEqualTo("Không tìm thấy dữ liệu");
        assertThat(EntityNames.vi("ProductionRequestLine")).isEqualTo("dòng phiếu sản xuất");
    }

    @Test void approvalStatusHasVietnameseLabels() {
        assertThat(java.util.Arrays.stream(ApprovalStatus.values()).map(ApprovalStatus::label))
                .containsExactly("Nháp", "Chờ duyệt", "Đã duyệt", "Bị từ chối");
    }
}
