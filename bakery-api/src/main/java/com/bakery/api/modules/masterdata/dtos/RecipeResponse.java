package com.bakery.api.modules.masterdata.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RecipeResponse {

    private UUID id;
    private Integer version;
    private Boolean isActive;
    private LocalDate effectiveDate;
    private String recipeType;
    private String note;
    private List<RecipeLineResponse> lines;

    /** Tổng cost nguyên liệu cho 1 đơn vị thành phẩm (VNĐ/cái hoặc VNĐ/kg) */
    private BigDecimal totalCostPerUnit;
}
