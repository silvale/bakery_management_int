package com.bakery.api.admin.ingredientprice;

import com.bakery.api.admin.ingredientprice.dto.IngredientPriceRequest;
import com.bakery.api.admin.ingredientprice.dto.IngredientPriceResponse;
import com.bakery.api.framework.controller.AdminBaseResource;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.IngredientPrice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý giá nguyên liệu theo version.
 *
 * Chỉ hỗ trợ CREATE (submit/approve tạo version mới).
 * Update và Delete bị khóa — lịch sử giá là bất biến.
 */
@RestController
@RequestMapping("/admin/ingredient-prices")
@RequiredArgsConstructor
@Tag(name = "Admin - Ingredient Prices", description = "Quản lý giá nguyên liệu (CREATE-only, approval workflow)")
public class IngredientPriceAdminController
        extends AdminBaseResource<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> {

    private final IngredientPriceSupportService supportService;
    private final IngredientPriceCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
