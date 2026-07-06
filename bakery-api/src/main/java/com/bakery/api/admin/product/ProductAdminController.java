package com.bakery.api.admin.product;

import com.bakery.api.admin.product.dto.ProductRequest;
import com.bakery.api.admin.product.dto.ProductResponse;
import com.bakery.api.framework.controller.AdminBaseResource;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.Product;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@Tag(name = "Admin - Products", description = "Quản lý sản phẩm (approval workflow)")
public class ProductAdminController
        extends AdminBaseResource<ProductRequest, ProductResponse, Product> {

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
