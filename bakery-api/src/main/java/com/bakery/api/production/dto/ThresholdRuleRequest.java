package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request để replace toàn bộ threshold rules của 1 sản phẩm.
 * Mỗi dayType gửi một danh sách rules theo thứ tự sort_order.
 */
public record ThresholdRuleRequest(
        @NotEmpty @Valid List<RuleItem> rules) {

    public record RuleItem(
            @NotBlank String dayType,          // WEEKDAY | WEEKEND
            int sortOrder,
            @NotBlank String conditionType,    // COUNT | PERCENT
            @NotNull BigDecimal conditionValue,
            @Positive int produceQty) {}
}
