package com.bakery.api.modules.partner;

import com.bakery.api.modules.partner.dtos.SupplierRequest;
import com.bakery.api.modules.partner.dtos.SupplierResponse;
import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.partner.entities.Supplier;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
@Tag(name = "Admin - Suppliers", description = "Quản lý nhà cung cấp (approval workflow)")
public class SupplierAdminController
        extends AdminBaseResource<SupplierRequest, SupplierResponse, Supplier, AdminFilter> {

    private final SupplierSupportService supportService;
    private final SupplierCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<SupplierRequest, SupplierResponse, Supplier> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<SupplierRequest, SupplierResponse, Supplier> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
