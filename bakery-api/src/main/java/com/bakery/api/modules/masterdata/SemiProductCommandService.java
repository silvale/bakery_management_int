package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.repositories.CommandRequestRepository;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.modules.masterdata.dtos.SemiProductRequest;
import com.bakery.api.modules.masterdata.dtos.SemiProductResponse;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class SemiProductCommandService
        extends AdminCommandService<SemiProductRequest, SemiProductResponse, SemiProduct> {

    public SemiProductCommandService(
            SemiProductSupportService support,
            SemiProductOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<SemiProductRequest> requestClass() {
        return SemiProductRequest.class;
    }
}
