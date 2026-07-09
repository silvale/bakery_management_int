package com.bakery.api.master.dto;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.entity.WarehouseType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WarehouseResponse extends BaseResponse {

    private String code;
    private String name;
    private WarehouseType warehouseType;
    private String address;
}
