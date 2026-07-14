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
    /** Tên sản phẩm — để hiển thị trên màn hình giao nhận */
    private String productName;
    private String productCode;
    private BigDecimal plannedQty;
    private BigDecimal qtyProduced;
    private BigDecimal qtyReceived;
    private BigDecimal discrepancy;
    private String deliveryStatus;
    private Instant confirmedAt;
    private String confirmedBy;
    private String note;
}
