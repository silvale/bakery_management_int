package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.IngredientRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientResponse;
import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
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
