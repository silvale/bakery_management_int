package com.bakery.api.admin.supplier;

import com.bakery.api.admin.supplier.dto.SupplierRequest;
import com.bakery.api.admin.supplier.dto.SupplierResponse;
import com.bakery.api.framework.controller.AdminBaseResource;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Supplier;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
@Tag(name = "Admin - Suppliers", description = "Quản lý nhà cung cấp (approval workflow)")
public class SupplierAdminController
        extends AdminBaseResource<SupplierRequest, SupplierResponse, Supplier> {

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
