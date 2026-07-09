package com.bakery.api.inventory.dto;

import java.time.LocalDate;
import java.util.List;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.entity.InventoryRequestType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InventoryRequestResponse extends BaseResponse {

    private String code;
    private InventoryRequestType requestType;
    private LocalDate requestDate;
    private LocalDate expectedDeliveryDate;
    private ReferenceValue sourceWarehouse;
    private ReferenceValue targetWarehouse;
    private ReferenceValue supplier;
    private String note;
    private List<InventoryRequestLineResponse> lines;
}
