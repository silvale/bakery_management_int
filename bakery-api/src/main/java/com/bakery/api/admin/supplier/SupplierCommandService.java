package com.bakery.api.admin.supplier;

import com.bakery.api.admin.supplier.dto.SupplierRequest;
import com.bakery.api.admin.supplier.dto.SupplierResponse;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.common.entity.Supplier;
import com.bakery.common.repository.CommandRequestRepository;
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
