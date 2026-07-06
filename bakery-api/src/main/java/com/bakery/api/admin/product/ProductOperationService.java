package com.bakery.api.admin.product;

import com.bakery.api.admin.product.dto.ProductRequest;
import com.bakery.api.admin.product.dto.ProductResponse;
import com.bakery.api.framework.service.AdminOperationService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Product;
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
