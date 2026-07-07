package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.ProductPriceRequest;
import com.bakery.api.modules.masterdata.dtos.ProductPriceResponse;
import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.ProductPrice;
import org.springframework.stereotype.Service;

@Service
public class ProductPriceOperationService
        extends AdminOperationService<ProductPriceRequest, ProductPriceResponse, ProductPrice> {

    public ProductPriceOperationService(
            ProductPriceSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
