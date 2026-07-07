package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.modules.masterdata.ProductPriceCommandService;
import com.bakery.api.modules.masterdata.ProductPriceSupportService;
import com.bakery.api.modules.masterdata.dtos.ProductPriceRequest;
import com.bakery.api.modules.masterdata.dtos.ProductPriceResponse;
import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.entities.ProductPrice;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý giá bán sản phẩm theo version.
 *
 * Chỉ hỗ trợ CREATE — mỗi thay đổi giá tạo version mới.
 * Giá có hiệu lực từ effective_date sau khi admin approve.
 * Update và Delete bị khóa — lịch sử giá là bất biến.
 */
@RestController
@RequestMapping("/admin/product-prices")
@RequiredArgsConstructor
@Tag(name = "Admin - Product Prices", description = "Quản lý giá bán sản phẩm (CREATE-only, approval workflow)")
public class ProductPriceAdminController
        extends AdminBaseResource<ProductPriceRequest, ProductPriceResponse, ProductPrice, AdminFilter> {

    private final ProductPriceSupportService supportService;
    private final ProductPriceCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<ProductPriceRequest, ProductPriceResponse, ProductPrice> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<ProductPriceRequest, ProductPriceResponse, ProductPrice> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
