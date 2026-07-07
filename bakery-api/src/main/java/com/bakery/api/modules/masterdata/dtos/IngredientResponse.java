package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.BaseUnit;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IngredientResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã nguyên liệu", order = 1)
    private String code;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên nguyên liệu", order = 2)
    private String name;

    @FieldCapability(
            filterable = true,
            dataType = DataType.ENUM,
            operators = {FilterOperator.EQ, FilterOperator.IN},
            label = "Đơn vị tính", order = 3)
    private BaseUnit baseUnit;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Đang hoạt động", order = 4)
    private Boolean isActive;
}
