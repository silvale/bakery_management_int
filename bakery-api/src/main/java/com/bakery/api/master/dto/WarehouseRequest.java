package com.bakery.api.master.dto;

import com.bakery.framework.entity.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WarehouseRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull WarehouseType warehouseType,
        String address) {}
