package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotNull
    private ProductType productType;

    /** PCS hoặc KG */
    @NotBlank
    @Size(max = 20)
    private String unit;

    @DecimalMin("0.0")
    private BigDecimal toleranceRate = BigDecimal.ZERO;

    private Boolean isActive = true;

    /**
     * Công thức sản xuất. Optional khi tạo (có thể thêm sau).
     * Khi có giá trị:
     *   - CREATE → tạo Recipe version 1
     *   - UPDATE → deactivate version cũ, tạo version mới (version++)
     */
    @Valid
    private RecipeRequest recipe;
}
