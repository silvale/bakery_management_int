package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.IngredientRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientResponse;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.framework.repositories.CommandRequestRepository;
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
