package com.bakery.api.production.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Manager điều chỉnh số lượng kế hoạch SX khi còn DRAFT.
 */
public record ProductionPlanAdjustRequest(
        @NotEmpty @Valid List<LineAdjust> lines) {

    public record LineAdjust(
            @NotNull UUID lineId,
            @NotNull Integer adjustedQty) {}
}
