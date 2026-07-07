package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.ProductRequest;
import com.bakery.api.modules.masterdata.dtos.ProductResponse;
import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.Product;
import org.springframework.stereotype.Service;

@Service
public class ProductOperationService
        extends AdminOperationService<ProductRequest, ProductResponse, Product> {

    public ProductOperationService(
            ProductSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
