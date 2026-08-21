/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.service.ItemService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import com.bakery.api.master.service.ItemService.PackagingRequest;
import com.bakery.api.master.service.ItemService.SuggestPackagingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Unified Item API — thay thế các controller riêng:
 *   /api/v1/ingredients    → dùng ?itemType=INGREDIENT
 *   /api/v1/semi-products  → dùng ?itemType=SEMI_PRODUCT
 *   /api/v1/products       → dùng ?itemType=PRODUCT
 *   (không có filter)      → load tất cả mọi loại
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController extends BakeryAdminResource<ItemRequest, ItemResponse> {

    private final ItemService service;

    @Override
    protected String screenCode() { return "ITEMS"; }

    @Override
    protected BakeryAdminService<ItemRequest, ItemResponse> getService() {
        return service;
    }

    /**
     * Upsert toàn bộ packaging list cho 1 item.
     * PUT /api/v1/items/{id}/packagings
     */
    @PutMapping("/{id}/packagings")
    public ResponseEntity<List<ItemResponse.PackagingResponse>> upsertPackagings(
            @PathVariable UUID id,
            @RequestBody List<PackagingRequest> requests) {
        return ResponseEntity.ok(service.upsertPackagings(id, requests));
    }

    /**
     * Gợi ý packaging phù hợp cho số lượng cần mua.
     * GET /api/v1/items/{id}/suggest-packaging?neededQty=15
     */
    @GetMapping("/{id}/suggest-packaging")
    public ResponseEntity<SuggestPackagingResponse> suggestPackaging(
            @PathVariable UUID id,
            @RequestParam BigDecimal neededQty) {
        SuggestPackagingResponse res = service.suggestPackaging(id, neededQty);
        if (res == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(res);
    }

    /**
     * Xuất toàn bộ nguyên liệu ra Excel.
     * Endpoint tạm — không yêu cầu phân quyền đặc biệt.
     *
     * <p>GET /api/v1/items/export
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        byte[] data = service.exportIngredients();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nguyen-lieu.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /**
     * Xóa hàng loạt items (soft-delete).
     * DELETE /api/v1/items/bulk
     * Body: ["uuid1", "uuid2", ...]
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkDelete(@RequestBody List<UUID> ids) {
        int count = service.bulkDelete(ids);
        return ResponseEntity.ok(Map.of("deleted", count, "ids", ids));
    }
}
