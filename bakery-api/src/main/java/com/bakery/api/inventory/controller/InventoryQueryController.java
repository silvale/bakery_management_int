package com.bakery.api.inventory.controller;

import com.bakery.api.inventory.dto.InventoryLotResponse;
import com.bakery.api.inventory.dto.InventoryStockResponse;
import com.bakery.api.inventory.service.InventoryQueryService;
import com.bakery.common.entity.enums.ItemType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API xem tồn kho theo kiến trúc Single-Table Ledger mới.
 *
 * Tất cả endpoints đều read-only — data vào qua phiếu IMPORT / TRANSFER / ADJUSTMENT.
 *
 * Màn hình kho:
 *   GET /api/v1/inventory?branchId=xxx                    — tổng tồn tất cả items
 *   GET /api/v1/inventory?branchId=xxx&itemType=INGREDIENT — chỉ nguyên liệu
 *   GET /api/v1/inventory?branchId=xxx&itemType=PRODUCT   — chỉ sản phẩm
 *
 * Chi tiết lô FEFO:
 *   GET /api/v1/inventory/lots?branchId=xxx&itemId=xxx&itemType=INGREDIENT
 *
 * Cảnh báo hết hạn:
 *   GET /api/v1/inventory/expiring?days=7
 *   GET /api/v1/inventory/expiring?days=7&branchId=xxx
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Stock", description = "Xem tồn kho theo kho — Single-Table Ledger (IMPORT/TRANSFER/ADJUSTMENT)")
public class InventoryQueryController {

    private final InventoryQueryService inventoryQueryService;

    /**
     * Tổng tồn kho theo từng item tại 1 chi nhánh.
     *
     * GET /api/v1/inventory?branchId=xxx
     * GET /api/v1/inventory?branchId=xxx&itemType=INGREDIENT
     * GET /api/v1/inventory?branchId=xxx&itemType=PRODUCT
     */
    @GetMapping
    @Operation(summary = "Tổng tồn kho theo item tại chi nhánh (group by item)")
    public ResponseEntity<List<InventoryStockResponse>> getStock(
            @RequestParam UUID branchId,
            @RequestParam(required = false) ItemType itemType) {

        return ResponseEntity.ok(inventoryQueryService.getStockByBranch(branchId, itemType));
    }

    /**
     * Chi tiết lô FEFO của 1 item tại 1 chi nhánh.
     *
     * GET /api/v1/inventory/lots?branchId=xxx&itemId=xxx&itemType=INGREDIENT
     * GET /api/v1/inventory/lots?branchId=xxx&itemId=xxx&itemType=INGREDIENT&includeEmpty=true
     */
    @GetMapping("/lots")
    @Operation(summary = "Chi tiết lô FEFO của 1 item (bao gồm ngày hết hạn, giá vốn, nguồn gốc)")
    public ResponseEntity<List<InventoryLotResponse>> getLots(
            @RequestParam UUID branchId,
            @RequestParam UUID itemId,
            @RequestParam(defaultValue = "INGREDIENT") ItemType itemType,
            @RequestParam(defaultValue = "false") boolean includeEmpty) {

        return ResponseEntity.ok(
                inventoryQueryService.getLotsByItem(branchId, itemId, itemType, includeEmpty));
    }

    /**
     * Danh sách các lô sắp hết hạn.
     *
     * GET /api/v1/inventory/expiring?days=7
     * GET /api/v1/inventory/expiring?days=3&branchId=xxx
     */
    @GetMapping("/expiring")
    @Operation(summary = "Cảnh báo lô hàng sắp hết hạn trong N ngày tới")
    public ResponseEntity<List<InventoryLotResponse>> getExpiring(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) UUID branchId) {

        return ResponseEntity.ok(inventoryQueryService.getExpiringSoon(branchId, days));
    }
}
