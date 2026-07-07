package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ProductPriceResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true,
            dataType = DataType.UUID,
            operators = {FilterOperator.EQ},
            label = "ID sản phẩm", order = 1)
    private UUID productId;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã sản phẩm", order = 2)
    private String productCode;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên sản phẩm", order = 3)
    private String productName;

    @FieldCapability(
            filterable = true, sortable = true,
            dataType = DataType.NUMBER,
            operators = {FilterOperator.GTE, FilterOperator.LTE, FilterOperator.BETWEEN},
            label = "Giá bán (VNĐ)", order = 4)
    private BigDecimal price;

    @FieldCapability(
            filterable = true, sortable = true,
            dataType = DataType.NUMBER,
            operators = {FilterOperator.EQ, FilterOperator.GTE, FilterOperator.LTE},
            label = "Phiên bản", order = 5)
    private Integer version;

    @FieldCapability(
            filterable = true, sortable = true,
            dataType = DataType.DATE,
            operators = {FilterOperator.GTE, FilterOperator.LTE, FilterOperator.BETWEEN},
            label = "Ngày hiệu lực", order = 6)
    private LocalDate effectiveDate;

    private String note;
}
