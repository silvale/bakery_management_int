package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StockLotResponse extends BaseResponse {

    private ReferenceValue item;
    private ReferenceValue warehouse;
    private ReferenceValue supplier;
    private BigDecimal qtyInitial;
    private BigDecimal qtyRemaining;
    private BigDecimal totalQtyRemaining;   // dùng cho /summary — tổng tất cả lot
    private BigDecimal unitCost;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
}
