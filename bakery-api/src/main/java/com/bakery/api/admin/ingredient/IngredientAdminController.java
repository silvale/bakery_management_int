package com.bakery.api.admin.ingredient;

import com.bakery.api.admin.ingredient.dto.IngredientRequest;
import com.bakery.api.admin.ingredient.dto.IngredientResponse;
import com.bakery.api.framework.controller.AdminBaseResource;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Ingredient;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ingredients")
@RequiredArgsConstructor
@Tag(name = "Admin - Ingredients", description = "Quản lý nguyên liệu (approval workflow)")
public class IngredientAdminController
        extends AdminBaseResource<IngredientRequest, IngredientResponse, Ingredient> {

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
