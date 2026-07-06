package com.bakery.api.admin.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierRequest {

    @NotBlank
    @Size(max = 20)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 500)
    private String address;

    @Size(max = 20)
    private String phone;

    private Boolean isActive = true;
}
