package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.BranchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchRequest {

    @NotBlank(message = "Code không được để trống")
    @Size(max = 20, message = "Code tối đa 20 ký tự")
    private String code;

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String name;

    private String address;

    private Boolean isMain = false;

    private Boolean isActive = true;

    @NotNull(message = "Branch type không được để trống")
    private BranchType branchType;
}
