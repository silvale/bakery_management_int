package com.bakery.api.inventory.dto;

import java.math.BigDecimal;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InventoryRequestLineResponse extends BaseResponse {

    private ReferenceValue item;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitCost;
    private Integer sortOrder;
    private String note;
}
