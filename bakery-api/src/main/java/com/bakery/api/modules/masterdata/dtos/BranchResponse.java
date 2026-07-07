package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã chi nhánh", order = 1)
    private String code;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên chi nhánh", order = 2)
    private String name;

    @FieldCapability(
            searchable = true,
            dataType = DataType.STRING,
            label = "Địa chỉ", order = 3)
    private String address;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Kho chính", order = 4)
    private Boolean isMain;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Đang hoạt động", order = 5)
    private Boolean isActive;

    @FieldCapability(
            filterable = true,
            dataType = DataType.ENUM,
            operators = {FilterOperator.EQ, FilterOperator.IN},
            label = "Loại kho", order = 6)
    private BranchType branchType;
}
