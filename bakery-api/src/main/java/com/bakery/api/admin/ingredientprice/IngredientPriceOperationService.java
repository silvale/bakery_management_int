package com.bakery.api.admin.ingredientprice;

import com.bakery.api.admin.ingredientprice.dto.IngredientPriceRequest;
import com.bakery.api.admin.ingredientprice.dto.IngredientPriceResponse;
import com.bakery.api.framework.service.AdminOperationService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.IngredientPrice;
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
