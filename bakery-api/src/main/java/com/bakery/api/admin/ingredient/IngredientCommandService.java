package com.bakery.api.admin.ingredient;

import com.bakery.api.admin.ingredient.dto.IngredientRequest;
import com.bakery.api.admin.ingredient.dto.IngredientResponse;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.common.entity.Ingredient;
import com.bakery.common.repository.CommandRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class IngredientCommandService
        extends AdminCommandService<IngredientRequest, IngredientResponse, Ingredient> {

    public IngredientCommandService(
            IngredientSupportService support,
            IngredientOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<IngredientRequest> requestClass() {
        return IngredientRequest.class;
    }
}
