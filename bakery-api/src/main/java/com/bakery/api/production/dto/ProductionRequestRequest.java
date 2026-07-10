package com.bakery.api.production.dto;

import java.time.LocalDate;
import java.util.List;

import com.bakery.framework.entity.ProductionType;

public record ProductionRequestRequest(
        ProductionType productionType,
        LocalDate productionDate,
        String note,
        List<ProductionRequestLineRequest> lines) {}
