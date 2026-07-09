package com.bakery.api.master.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProductExpiryConfigRequest(
        @NotNull UUID itemId,
        @NotNull @Min(0) Integer shelfDays) {}
