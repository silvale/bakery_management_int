package com.bakery.api.modules.inventory.controllers;

import com.bakery.api.framework.controllers.QueryBaseResource;
import com.bakery.api.framework.dtos.PageResult;
import com.bakery.api.framework.enums.ItemType;
import com.bakery.api.modules.inventory.dtos.InventoryFilter;
import com.bakery.api.modules.inventory.dtos.InventoryLotResponse;
import com.bakery.api.modules.inventory.dtos.InventoryStockResponse;
import com.bakery.api.modules.inventory.services.InventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only inventory stock views — Single-Table Ledger.
 *
 * Extends QueryBaseResource → GET /active?branchId=&itemType= (list + filter)
 * Custom endpoints bổ sung: /lots (FEFO), /expiring (cảnh báo)
 *
 * GET /api/v1/inventory/active?branchId=xxx                    — tổng tồn tất cả items
 * GET /api/v1/inventory/active?branchId=xxx&itemType=INGREDIENT — chỉ nguyên liệu
 * GET /api/v1/inventory/lots?branchId=xxx&itemId=xxx            — chi tiết lô FEFO
 * GET /api/v1/inventory/expiring?days=7                         — cảnh báo hết hạn
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Stock", description = "Xem tồn kho theo kho — Single-Table Ledger (read-only)")
public class InventoryQueryController
        extends QueryBaseResource<InventoryFilter, InventoryStockResponse> {

    private final InventoryQueryService inventoryQueryService;

    // ── QueryBaseResource: GET /active?branchId=&itemType= ────

    @Override
    protected PageResult<InventoryStockResponse> listData(InventoryFilter filter) {
        List<InventoryStockResponse> all =
                inventoryQueryService.getStockByBranch(filter.getBranchId(), filter.getItemType());

        int page = filter.getPage();
        int size = filter.getSize();
        int from = page * size;
        int to   = Math.min(from + size, all.size());
        List<InventoryStockResponse> paged = (from < all.size()) ? all.subList(from, to) : List.of();

        return PageResult.ofList(paged, all.size(), page, size);
    }

    // ── Custom endpoints ──────────────────────────────────────

    /**
     * Chi tiết lô FEFO của 1 item tại 1 chi nhánh.
     * GET /api/v1/inventory/lots?branchId=xxx&itemId=xxx&itemType=INGREDIENT
     */
    @GetMapping("/lots")
    @Operation(summary = "Chi tiết lô FEFO của 1 item (ngày hết hạn, giá vốn, nguồn gốc)")
    public ResponseEntity<List<InventoryLotResponse>> getLots(
            @RequestParam UUID branchId,
            @RequestParam UUID itemId,
            @RequestParam(defaultValue = "INGREDIENT") ItemType itemType,
            @RequestParam(defaultValue = "false") boolean includeEmpty) {

        return ResponseEntity.ok(
                inventoryQueryService.getLotsByItem(branchId, itemId, itemType, includeEmpty));
    }

    /**
     * Cảnh báo các lô sắp hết hạn trong N ngày tới.
     * GET /api/v1/inventory/expiring?days=7&branchId=xxx
     */
    @GetMapping("/expiring")
    @Operation(summary = "Cảnh báo lô hàng sắp hết hạn trong N ngày tới")
    public ResponseEntity<List<InventoryLotResponse>> getExpiring(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(inventoryQueryService.getExpiringSoon(branchId, days));
    }
}
