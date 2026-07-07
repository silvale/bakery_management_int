package com.bakery.api.modules.partner.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã nhà cung cấp", order = 1)
    private String code;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên nhà cung cấp", order = 2)
    private String name;

    @FieldCapability(
            searchable = true,
            dataType = DataType.STRING,
            label = "Địa chỉ", order = 3)
    private String address;

    @FieldCapability(
            searchable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Số điện thoại", order = 4)
    private String phone;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Đang hoạt động", order = 5)
    private Boolean isActive;
}
