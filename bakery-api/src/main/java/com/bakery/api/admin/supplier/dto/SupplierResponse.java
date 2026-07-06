package com.bakery.api.admin.supplier.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierResponse extends BakeryBaseResponse {

    private String code;
    private String name;
    private String address;
    private String phone;
    private Boolean isActive;
}
