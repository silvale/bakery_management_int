package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.SemiProductType;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Response DTO cho SemiProduct.
 */
@Getter
@Setter
public class SemiProductResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã BTP", order = 1)
    private String code;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên BTP", order = 2)
    private String name;

    @FieldCapability(
            filterable = true,
            dataType = DataType.ENUM,
            operators = {FilterOperator.EQ, FilterOperator.IN},
            label = "Loại BTP", order = 3)
    private SemiProductType type;

    @FieldCapability(
            filterable = true, sortable = true,
            dataType = DataType.NUMBER,
            operators = {FilterOperator.GTE, FilterOperator.LTE},
            label = "Năng suất (kg)", order = 4)
    private BigDecimal totalYieldKg;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Đang hoạt động", order = 5)
    private Boolean isActive;
}
