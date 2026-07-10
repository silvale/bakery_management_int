package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeliveryRecordResponse {
    private UUID id;
    private BigDecimal qtyProduced;
    private BigDecimal qtyReceived;
    private BigDecimal discrepancy;
    private String deliveryStatus;
    private Instant confirmedAt;
    private String confirmedBy;
    private String note;
}
