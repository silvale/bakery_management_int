package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.BakeryBaseResponse;
import com.bakery.api.framework.enums.ProductType;
import com.bakery.api.framework.meta.DataType;
import com.bakery.api.framework.meta.FieldCapability;
import com.bakery.api.framework.meta.FilterOperator;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductResponse extends BakeryBaseResponse {

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ, FilterOperator.ILIKE},
            label = "Mã sản phẩm", order = 1)
    private String code;

    @FieldCapability(
            filterable = true, searchable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.ILIKE},
            label = "Tên sản phẩm", order = 2)
    private String name;

    @FieldCapability(
            filterable = true,
            dataType = DataType.ENUM,
            operators = {FilterOperator.EQ, FilterOperator.IN},
            label = "Loại sản phẩm", order = 3)
    private ProductType productType;

    @FieldCapability(
            filterable = true, sortable = true,
            dataType = DataType.STRING,
            operators = {FilterOperator.EQ},
            label = "Đơn vị", order = 4)
    private String unit;

    @FieldCapability(
            filterable = true,
            dataType = DataType.NUMBER,
            operators = {FilterOperator.GTE, FilterOperator.LTE},
            label = "Tỷ lệ hao hụt", order = 5)
    private BigDecimal toleranceRate;

    @FieldCapability(
            filterable = true,
            dataType = DataType.BOOLEAN,
            operators = {FilterOperator.EQ},
            label = "Đang hoạt động", order = 6)
    private Boolean isActive;

    /** Công thức đang active (kèm cost từng dòng). Null nếu chưa có công thức. */
    private RecipeResponse activeRecipe;

    /** Danh sách EX_CODE (SKU POS) + giá bán hiện tại */
    private List<ExCodeEntry> exCodes;

    @Getter @Setter
    public static class ExCodeEntry {
        private String  skuCode;
        private Short   productionDay;   // 2=T2...7=T7, 0=CN, null=mỗi ngày
        private String  skuSource;       // POS | LEGACY | MANUAL
        private BigDecimal currentPrice; // giá bán hiện tại (VNĐ/unit)
        private String  priceUnit;       // VNĐ/cái hoặc VNĐ/kg
    }
}
