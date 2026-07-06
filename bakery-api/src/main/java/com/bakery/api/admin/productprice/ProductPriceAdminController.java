package com.bakery.api.admin.productprice;

import com.bakery.api.admin.productprice.dto.ProductPriceRequest;
import com.bakery.api.admin.productprice.dto.ProductPriceResponse;
import com.bakery.api.framework.controller.AdminBaseResource;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.api.framework.service.EntityHistoryService;
import com.bakery.common.entity.ProductPrice;
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
        extends AdminBaseResource<ProductPriceRequest, ProductPriceResponse, ProductPrice> {

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
