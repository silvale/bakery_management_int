package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductionGroupRequest(
        @NotBlank String code,
        @NotBlank String name,
        /** FREE_GROUP | BATCH_FORMULA */
        @NotBlank String groupType,
        UUID itemGroupId,
        // FREE_GROUP
        Integer targetWeekday,
        Integer targetWeekend,
        // BATCH_FORMULA
        Integer batchWeightGrams,
        String note,
        @Valid List<ProductionGroupItemRequest> items) {

    public record ProductionGroupItemRequest(
            @NotNull UUID itemId,
            /** Gram/cái — chỉ bắt buộc với BATCH_FORMULA. */
            BigDecimal gramsPerUnit,
            int sortOrder) {}
}
