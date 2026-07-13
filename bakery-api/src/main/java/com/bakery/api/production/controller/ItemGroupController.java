package com.bakery.api.production.controller;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ItemGroupRequest;
import com.bakery.api.production.dto.ItemGroupResponse;
import com.bakery.api.production.service.ItemGroupService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý phòng/bộ phận sản xuất (PL, PK, BMN...).
 */
@RestController
@RequestMapping("/api/v1/item-groups")
@RequiredArgsConstructor
public class ItemGroupController {

    private final ItemGroupService service;

    @GetMapping
    public List<ItemGroupResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ItemGroupResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemGroupResponse create(@Valid @RequestBody ItemGroupRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ItemGroupResponse update(@PathVariable UUID id, @Valid @RequestBody ItemGroupRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
