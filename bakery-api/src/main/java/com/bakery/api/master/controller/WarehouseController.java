package com.bakery.api.master.controller;

import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.dto.WarehouseResponse;
import com.bakery.api.master.service.WarehouseService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController extends BakeryAdminResource<WarehouseRequest, WarehouseResponse> {

    private final WarehouseService service;

    @Override
    protected String screenCode() { return "WAREHOUSES"; }   // VIEW: mọi vai trò (đã lọc theo kho được gán); sửa/xóa: Admin

    @Override
    protected BakeryAdminService<WarehouseRequest, WarehouseResponse> getService() {
        return service;
    }

    /**
     * 11/BE-8: {@code GET /warehouses?scope=all} — MỌI kho đang dùng, chỉ {id, code, name, type} (không tồn, không số liệu),
     * để chọn kho xuất của phiếu điều chuyển (bếp xin hàng từ Kho Tổng). Cần {@code INVENTORY_REQUESTS:CREATE}
     * (Admin luôn được). Không có {@code scope} → danh sách kho được gán như thường (phương thức kế thừa).
     */
    @org.springframework.web.bind.annotation.GetMapping(params = "scope=all")
    @com.bakery.framework.security.RequirePermission(screen = "INVENTORY_REQUESTS", action = "CREATE")
    public org.springframework.http.ResponseEntity<com.bakery.framework.dto.PageResult<com.bakery.api.master.dto.WarehouseRefResponse>> listAllForTransfer() {
        java.util.List<com.bakery.api.master.dto.WarehouseRefResponse> all = service.allForTransfer();
        return org.springframework.http.ResponseEntity.ok(com.bakery.framework.dto.PageResult.of(
                new org.springframework.data.domain.PageImpl<>(all, org.springframework.data.domain.PageRequest.of(0, Math.max(all.size(), 1)), all.size())));
    }
}
