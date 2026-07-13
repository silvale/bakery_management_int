/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.service.ItemService;
import com.bakery.framework.dto.PageResult;
import com.bakery.framework.dto.RejectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unified Item API — thay thế các controller riêng:
 *   /api/v1/ingredients    → dùng ?itemType=INGREDIENT
 *   /api/v1/semi-products  → dùng ?itemType=SEMI_PRODUCT
 *   /api/v1/products       → dùng ?itemType=PRODUCT
 *
 * <p>FE chỉ cần một trang, filter theo itemType khi cần.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    // ── Listing ───────────────────────────────────────────────

    /** Tải toàn bộ hoặc filter theo ?itemType=INGREDIENT|SEMI_PRODUCT|PRODUCT */
    @GetMapping
    public ResponseEntity<PageResult<ItemResponse>> list(
            @RequestParam MultiValueMap<String, String> params, Pageable pageable) {
        return ResponseEntity.ok(itemService.findAll(params, pageable));
    }

    /** Tải tất cả không phân trang (dùng cho dropdown, autocomplete) */
    @GetMapping("/all")
    public ResponseEntity<List<ItemResponse>> listAll() {
        return ResponseEntity.ok(itemService.findAll());
    }

    // ── Detail ────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ItemResponse> getById(@PathVariable UUID id) {
        return itemService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────

    /** itemType bắt buộc trong body: INGREDIENT | SEMI_PRODUCT | PRODUCT */
    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request) {
        return ResponseEntity.status(201).body(itemService.create(request));
    }

    // ── Update ────────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<ItemResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(itemService.update(id, request));
    }

    // ── Delete ────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Approval ──────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    public ResponseEntity<ItemResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(itemService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ItemResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(itemService.reject(id, request.reason()));
    }
}
