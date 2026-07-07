package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.SemiProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Request DTO cho tạo/cập nhật SemiProduct (Phôi/Nhân).
 */
@Getter
@Setter
public class SemiProductRequest {

    @NotBlank(message = "Code không được để trống")
    private String code;

    @NotBlank(message = "Tên không được để trống")
    private String name;

    @NotNull(message = "Type không được để trống")
    private SemiProductType type;

    @NotNull(message = "total_yield_kg không được để trống")
    @Positive(message = "total_yield_kg phải lớn hơn 0")
    private BigDecimal totalYieldKg;

    private Boolean isActive = true;
}
