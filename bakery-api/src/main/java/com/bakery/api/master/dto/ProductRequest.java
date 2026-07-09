package com.bakery.api.master.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record ProductRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String unit,
        String productType,
        String productCategory,
        BigDecimal sellingPrice) {}
