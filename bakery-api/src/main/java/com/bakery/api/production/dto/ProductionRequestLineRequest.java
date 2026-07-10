package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionRequestLineRequest(
        UUID productId,
        /** Nếu null → service tự lấy recipe active của product */
        UUID recipeId,
        BigDecimal plannedQty,
        Integer sortOrder,
        String note) {}
