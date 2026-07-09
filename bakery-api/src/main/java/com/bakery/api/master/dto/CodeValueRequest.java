package com.bakery.api.master.dto;

import jakarta.validation.constraints.NotBlank;

public record CodeValueRequest(
        @NotBlank String groupKey,
        @NotBlank String code,
        @NotBlank String name,
        Integer sortOrder) {}
