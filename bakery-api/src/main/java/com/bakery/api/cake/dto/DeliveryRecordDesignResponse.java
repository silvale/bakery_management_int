package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeliveryRecordDesignResponse {
    private UUID designId;
    /** key = mã mẫu, name = tên mẫu. */
    private ReferenceValue design;
    private String imageUrl;
    private BigDecimal qtyProduced;
    private BigDecimal qtyReceived;
}
