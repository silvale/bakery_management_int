package com.bakery.api.master.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductMappingRequest(UUID itemId, String exCode, BigDecimal sellingPrice, String note) {}
