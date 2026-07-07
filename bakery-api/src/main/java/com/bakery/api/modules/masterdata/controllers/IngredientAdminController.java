package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.modules.masterdata.IngredientCommandService;
import com.bakery.api.modules.masterdata.IngredientSupportService;
import com.bakery.api.modules.masterdata.dtos.IngredientRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientResponse;
import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ingredients")
@RequiredArgsConstructor
@Tag(name = "Admin - Ingredients", description = "Quản lý nguyên liệu (approval workflow)")
public class IngredientAdminController
        extends AdminBaseResource<IngredientRequest, IngredientResponse, Ingredient, AdminFilter> {

    private final IngredientSupportService supportService;
    private final IngredientCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<IngredientRequest, IngredientResponse, Ingredient> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<IngredientRequest, IngredientResponse, Ingredient> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
