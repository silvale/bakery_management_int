package com.bakery.api.production.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ProductionGroupRequest;
import com.bakery.api.production.dto.ProductionGroupResponse;
import com.bakery.api.production.service.ProductionGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý nhóm sản xuất (FREE_GROUP / BATCH_FORMULA).
 */
@RestController
@RequestMapping("/api/v1/production-groups")
@RequiredArgsConstructor
public class ProductionGroupController {

    private final ProductionGroupService service;

    @GetMapping
    public List<ProductionGroupResponse> findAll(
            @RequestParam(required = false) UUID itemGroupId) {
        return itemGroupId != null
                ? service.findByItemGroup(itemGroupId)
                : service.findAll();
    }

    @GetMapping("/{id}")
    public ProductionGroupResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionGroupResponse create(@Valid @RequestBody ProductionGroupRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ProductionGroupResponse update(
            @PathVariable UUID id, @Valid @RequestBody ProductionGroupRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
