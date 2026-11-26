package com.bakery.api.master.dto;

import jakarta.validation.constraints.NotBlank;

public record StoreRequest(@NotBlank String code, @NotBlank String name, Integer sortOrder) {}
