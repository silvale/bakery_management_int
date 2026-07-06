package com.bakery.api.admin.ingredient;

import com.bakery.api.admin.ingredient.dto.IngredientRequest;
import com.bakery.api.admin.ingredient.dto.IngredientResponse;
import com.bakery.api.framework.service.AdminOperationService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Ingredient;
import org.springframework.stereotype.Service;

@Service
public class IngredientOperationService
        extends AdminOperationService<IngredientRequest, IngredientResponse, Ingredient> {

    public IngredientOperationService(
            IngredientSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
