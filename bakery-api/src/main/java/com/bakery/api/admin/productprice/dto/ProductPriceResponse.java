package com.bakery.api.admin.productprice.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ProductPriceResponse extends BakeryBaseResponse {

    private UUID productId;
    private String productCode;
    private String productName;
    private BigDecimal price;
    private Integer version;
    private LocalDate effectiveDate;
    private String note;
}
