package com.bakery.api.master.dto;

import java.util.UUID;

import com.bakery.api.master.entity.WarehouseKind;
import com.bakery.framework.dto.BaseResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WarehouseResponse extends BaseResponse {

    private String code;
    private String name;
    private String address;
    private WarehouseKind kind;
    private boolean canReceivePurchase;
    @JsonProperty("isProductionSource")
    private boolean productionSource;
    private boolean canStoreSemi;
    @JsonProperty("isDeliveryHold")
    private boolean deliveryHold;
    @JsonProperty("isSalesPoint")
    private boolean salesPoint;
    private boolean canExportExternal;
    private int sortOrder;
    /** 23: cửa hàng chứa kho; null = không thuộc cửa hàng. Luôn trả, không phụ thuộc WAREHOUSES:VIEW. */
    private StoreRef store;

    /** Chỉ có khi user có WAREHOUSES:VIEW (màn quản lý kho); null = không trả. */
    private Long stockLotCount;
    private Long userCount;
    private Long itemCount;

    public record StoreRef(UUID id, String code, String name, int sortOrder) {}
}
