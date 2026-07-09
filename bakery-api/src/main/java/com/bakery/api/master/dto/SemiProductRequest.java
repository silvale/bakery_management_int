package com.bakery.api.master.dto;

import jakarta.validation.constraints.NotBlank;

public record SemiProductRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String unit) {}
