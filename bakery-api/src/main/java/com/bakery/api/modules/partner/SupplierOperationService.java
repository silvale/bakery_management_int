package com.bakery.api.modules.partner;

import com.bakery.api.modules.partner.dtos.SupplierRequest;
import com.bakery.api.modules.partner.dtos.SupplierResponse;
import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.partner.entities.Supplier;
import org.springframework.stereotype.Service;

@Service
public class SupplierOperationService
        extends AdminOperationService<SupplierRequest, SupplierResponse, Supplier> {

    public SupplierOperationService(
            SupplierSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
