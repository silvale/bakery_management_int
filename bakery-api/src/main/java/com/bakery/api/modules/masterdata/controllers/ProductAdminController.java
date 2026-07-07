package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.modules.masterdata.ProductCommandService;
import com.bakery.api.modules.masterdata.ProductSupportService;
import com.bakery.api.modules.masterdata.dtos.ProductRequest;
import com.bakery.api.modules.masterdata.dtos.ProductResponse;
import com.bakery.api.modules.masterdata.entities.Product;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin - Products", description = "Quản lý sản phẩm (approval workflow)")
public class ProductAdminController
        extends AdminBaseResource<ProductRequest, ProductResponse, Product, AdminFilter> {

    private final ProductSupportService supportService;
    private final ProductCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<ProductRequest, ProductResponse, Product> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<ProductRequest, ProductResponse, Product> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
