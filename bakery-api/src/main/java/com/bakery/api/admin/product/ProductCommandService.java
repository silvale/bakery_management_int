package com.bakery.api.admin.product;

import com.bakery.api.admin.product.dto.ProductRequest;
import com.bakery.api.admin.product.dto.ProductResponse;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.common.entity.Product;
import com.bakery.common.repository.CommandRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ProductCommandService
        extends AdminCommandService<ProductRequest, ProductResponse, Product> {

    public ProductCommandService(
            ProductSupportService support,
            ProductOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<ProductRequest> requestClass() {
        return ProductRequest.class;
    }
}
