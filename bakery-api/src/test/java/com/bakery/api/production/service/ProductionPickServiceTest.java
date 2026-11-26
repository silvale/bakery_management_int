package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ProductionPoolDto.PickLineRequest;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.framework.entity.ApprovalStatus;
import org.junit.jupiter.api.Test;

/** 24/BE-2: kiểm yêu cầu tạo phiếu lấy / lấy thêm (§5.2, §5.4). */
class ProductionPickServiceTest {

    private static PickLineRequest line(String qty, String damaged) {
        return new PickLineRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(qty), damaged == null ? null : new BigDecimal(damaged));
    }

    @Test void reasonAndNoteRules() {
        assertThat(ProductionPickService.requestBlocker("PLAN", null, List.of(line("1", null)))).contains("Lý do lấy không hợp lệ");
        assertThat(ProductionPickService.requestBlocker("EXTRA_OTHER", " ", List.of(line("1", null)))).isEqualTo("Lấy thêm lý do Khác phải ghi chú");
        assertThat(ProductionPickService.requestBlocker("EXTRA_OTHER", "khách đổi ý", List.of(line("1", null)))).isNull();
        assertThat(ProductionPickService.requestBlocker("MANUAL", null, List.of())).isEqualTo("Chưa chọn mặt hàng nào");
    }

    @Test void damagedQtyOnlyWithExtraDamaged() {
        assertThat(ProductionPickService.requestBlocker("EXTRA_DAMAGED", null, List.of(line("1", "0.5")))).isNull();
        assertThat(ProductionPickService.requestBlocker("EXTRA_CUSTOMER", null, List.of(line("1", "0.5")))).contains("chỉ dùng với lý do NL hư hỏng");
        assertThat(ProductionPickService.requestBlocker("MANUAL", null, List.of(line("0", null)))).contains("lớn hơn 0");
    }

    @Test void extraNeedsAnApprovedUnfinishedProductionRequest() {
        assertThat(ProductionPickService.extraTargetBlocker(null)).isEqualTo("Lấy thêm NL phải gắn một phiếu SX");
        ProductionRequest draft = new ProductionRequest();
        draft.setCode("PR-1");
        draft.setApprovalStatus(ApprovalStatus.DRAFT);
        assertThat(ProductionPickService.extraTargetBlocker(draft)).contains("chưa duyệt hoặc đã hoàn thành");
    }

    @Test void shortMessageHidesNumbersFromThoseWhoCannotSeeStock() {   // 24/§5.8
        assertThat(ProductionPickService.shortMessage(true, "Thịt heo", "Phòng Lạnh", new java.math.BigDecimal("2.5")))
                .isEqualTo("Thịt heo: kho Phòng Lạnh chỉ còn 2.5");
        String hidden = ProductionPickService.shortMessage(false, "Thịt heo", "Phòng Lạnh", new java.math.BigDecimal("2.5"));
        assertThat(hidden).isEqualTo("Thịt heo: kho Phòng Lạnh không đủ. Báo quản lý kho bổ sung rồi xác nhận lại.");
        assertThat(hidden).doesNotContain("2");
    }
}
