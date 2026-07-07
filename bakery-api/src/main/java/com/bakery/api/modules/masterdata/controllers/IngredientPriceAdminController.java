package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.modules.masterdata.IngredientPriceCommandService;
import com.bakery.api.modules.masterdata.IngredientPriceSupportService;
import com.bakery.api.modules.masterdata.dtos.IngredientPriceRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientPriceResponse;
import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.IngredientPrice;
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
        extends AdminBaseResource<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice, AdminFilter> {

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
