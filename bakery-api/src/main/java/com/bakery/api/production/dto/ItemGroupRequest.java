package com.bakery.api.production.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemGroupRequest(
        @NotBlank String code,
        @NotBlank String name,
        int sortOrder) {}
