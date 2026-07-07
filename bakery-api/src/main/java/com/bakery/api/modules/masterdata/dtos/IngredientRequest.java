package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.BaseUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IngredientRequest {

    @NotBlank(message = "Code không được để trống")
    @Size(max = 50, message = "Code tối đa 50 ký tự")
    private String code;

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 200, message = "Tên tối đa 200 ký tự")
    private String name;

    @NotNull(message = "Base unit không được để trống")
    private BaseUnit baseUnit;

    private Boolean isActive = true;
}
