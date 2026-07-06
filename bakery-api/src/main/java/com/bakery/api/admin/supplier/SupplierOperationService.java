package com.bakery.api.admin.supplier;

import com.bakery.api.admin.supplier.dto.SupplierRequest;
import com.bakery.api.admin.supplier.dto.SupplierResponse;
import com.bakery.api.framework.service.AdminOperationService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Supplier;
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
