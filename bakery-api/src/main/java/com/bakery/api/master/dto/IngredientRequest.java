package com.bakery.api.master.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record IngredientRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String unit,
        String ingredientType,
        UUID defaultSupplierId) {}
