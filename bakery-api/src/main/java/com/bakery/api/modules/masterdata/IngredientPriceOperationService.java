package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.IngredientPriceRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientPriceResponse;
import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.IngredientPrice;
import org.springframework.stereotype.Service;

@Service
public class IngredientPriceOperationService
        extends AdminOperationService<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> {

    public IngredientPriceOperationService(
            IngredientPriceSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
