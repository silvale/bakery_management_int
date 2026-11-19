package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkPaidResponse(int paidCount, BigDecimal totalAmount, Instant paidAt) {}
