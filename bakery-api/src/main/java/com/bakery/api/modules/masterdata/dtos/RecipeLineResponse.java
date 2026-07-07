package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.RecipeLineType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class RecipeLineResponse {

    private UUID id;

    // ingredient (nếu dòng này là nguyên liệu thô)
    private UUID   ingredientId;
    private String ingredientCode;
    private String ingredientName;

    // semi_product (nếu dòng này là bán thành phẩm)
    private UUID   semiProductId;
    private String semiProductCode;
    private String semiProductName;

    private BigDecimal quantityGram;
    private RecipeLineType lineType;

    /** Giá/kg của NL hoặc cost/kg của BTP tại thời điểm hiện tại */
    private BigDecimal unitPricePerKg;

    /** Cost đóng góp của dòng này vào 1 đơn vị thành phẩm (VNĐ) */
    private BigDecimal costContribution;

    private String note;
}
