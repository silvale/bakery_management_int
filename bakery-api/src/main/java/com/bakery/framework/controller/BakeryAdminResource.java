package com.bakery.framework.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.bakery.framework.dto.PageResult;
import com.bakery.framework.dto.RejectRequest;
import com.bakery.framework.service.BakeryAdminService;

import jakarta.validation.Valid;

/**
 * Base controller for all Bakery admin endpoints.
 * Provides standard CRUD + listing out of the box.
 * Concrete controllers extend this and supply the service.
 *
 * @param <REQ> request DTO
 * @param <RES> response DTO
 */
public abstract class BakeryAdminResource<REQ, RES> {

    protected abstract BakeryAdminService<REQ, RES> getService();

    // ── Listing ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<PageResult<RES>> list(
            @RequestParam MultiValueMap<String, String> params, Pageable pageable) {
        Page<RES> page = getService().findAll(params, pageable);
        return ResponseEntity.ok(PageResult.of(page));
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<RES> getById(@PathVariable UUID id) {
        return getService().findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ───────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RES> create(@Valid @RequestBody REQ request) {
        RES result = getService().create(request);
        return ResponseEntity.status(201).body(result);
    }

    // ── Update ───────────────────────────────────────────────

    @PutMapping("/{id}")
    public ResponseEntity<RES> update(@PathVariable UUID id, @Valid @RequestBody REQ request) {
        RES result = getService().update(id, request);
        return ResponseEntity.ok(result);
    }

    // ── Delete ───────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        getService().delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Bulk ─────────────────────────────────────────────────

    @GetMapping("/all")
    public ResponseEntity<List<RES>> listAll() {
        return ResponseEntity.ok(getService().findAll());
    }

    // ── Approval ─────────────────────────────────────────────

    @PostMapping("/{id}/approve")
    public ResponseEntity<RES> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(getService().approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RES> reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(getService().reject(id, request.reason()));
    }
}
