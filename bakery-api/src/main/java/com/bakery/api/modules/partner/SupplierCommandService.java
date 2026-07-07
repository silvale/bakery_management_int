package com.bakery.api.modules.partner;

import com.bakery.api.modules.partner.dtos.SupplierRequest;
import com.bakery.api.modules.partner.dtos.SupplierResponse;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.modules.partner.entities.Supplier;
import com.bakery.api.framework.repositories.CommandRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class SupplierCommandService
        extends AdminCommandService<SupplierRequest, SupplierResponse, Supplier> {

    public SupplierCommandService(
            SupplierSupportService support,
            SupplierOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<SupplierRequest> requestClass() {
        return SupplierRequest.class;
    }
}
