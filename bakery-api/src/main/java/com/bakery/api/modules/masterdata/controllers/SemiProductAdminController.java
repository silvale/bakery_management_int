package com.bakery.api.modules.masterdata.controllers;

import com.bakery.api.framework.controllers.AdminBaseResource;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.SemiProductCommandService;
import com.bakery.api.modules.masterdata.SemiProductSupportService;
import com.bakery.api.modules.masterdata.dtos.SemiProductFilter;
import com.bakery.api.modules.masterdata.dtos.SemiProductRequest;
import com.bakery.api.modules.masterdata.dtos.SemiProductResponse;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin CRUD cho Bán thành phẩm (Phôi, Nhân) — với approval workflow.
 *
 * Endpoints kế thừa từ AdminBaseResource:
 *   GET  /admin/semi-products/active?search=&type=PHOI  — danh sách (filter)
 *   GET  /admin/semi-products/{id}                      — chi tiết
 *   GET  /admin/semi-products/pending                   — chờ duyệt
 *   GET  /admin/semi-products/rejected                  — đã từ chối
 *   POST /admin/semi-products/submit/create             — tạo mới (→ PENDING)
 *   POST /admin/semi-products/submit/update/{id}        — cập nhật (→ PENDING)
 *   POST /admin/semi-products/submit/delete/{id}        — xóa (→ PENDING)
 *   POST /admin/semi-products/approve/{cmdId}           — duyệt
 *   POST /admin/semi-products/reject/{cmdId}            — từ chối
 *   GET  /admin/semi-products/{id}/history              — lịch sử thay đổi
 */
@RestController
@RequestMapping("/admin/semi-products")
@RequiredArgsConstructor
@Tag(name = "Admin - Semi Products", description = "Quản lý bán thành phẩm: Phôi / Nhân (approval workflow)")
public class SemiProductAdminController
        extends AdminBaseResource<SemiProductRequest, SemiProductResponse, SemiProduct, SemiProductFilter> {

    private final SemiProductSupportService supportService;
    private final SemiProductCommandService commandService;
    private final EntityHistoryService historyService;

    @Override
    protected AdminEntitySupportService<SemiProductRequest, SemiProductResponse, SemiProduct> abstractSupport() {
        return supportService;
    }

    @Override
    protected AdminCommandService<SemiProductRequest, SemiProductResponse, SemiProduct> abstractCommand() {
        return commandService;
    }

    @Override
    protected EntityHistoryService abstractHistory() {
        return historyService;
    }
}
