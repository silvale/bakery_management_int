package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.ProductRequest;
import com.bakery.api.modules.masterdata.dtos.ProductResponse;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.framework.repositories.CommandRequestRepository;
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
