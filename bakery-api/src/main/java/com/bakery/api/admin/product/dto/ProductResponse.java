package com.bakery.api.admin.product.dto;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.common.entity.enums.ProductType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductResponse extends BakeryBaseResponse {

    private String code;
    private String name;
    private ProductType productType;
    private String unit;
    private BigDecimal toleranceRate;
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
