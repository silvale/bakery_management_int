package com.bakery.api.production.dto;

import java.math.BigDecimal;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductionRequestLineResponse extends BaseResponse {
    private ReferenceValue product;
    private ReferenceValue recipe;
    private BigDecimal plannedQty;
    private String lineStatus;
    private Integer sortOrder;
    private String note;
    private DeliveryRecordResponse deliveryRecord;
}
