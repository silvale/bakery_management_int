package com.bakery.api.admin.productprice;

import com.bakery.api.admin.productprice.dto.ProductPriceRequest;
import com.bakery.api.admin.productprice.dto.ProductPriceResponse;
import com.bakery.api.framework.service.AdminOperationService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.ProductPrice;
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
