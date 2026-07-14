package com.bakery.api.master.dto;

import java.math.BigDecimal;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductMappingResponse extends BaseResponse {

    private ReferenceValue item;
    private String exCode;
    private BigDecimal sellingPrice;
    private String note;
}
