package com.bakery.api.production.service;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ItemGroupRequest;
import com.bakery.api.production.dto.ItemGroupResponse;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.repository.ItemGroupRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemGroupService {

    private final ItemGroupRepository repository;

    public List<ItemGroupResponse> findAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(ItemGroupResponse::from)
                .toList();
    }

    public ItemGroupResponse findById(UUID id) {
        return ItemGroupResponse.from(getById(id));
    }

    @Transactional
    public ItemGroupResponse create(ItemGroupRequest req) {
        ItemGroup e = new ItemGroup();
        e.setCode(req.code().toUpperCase());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
        return ItemGroupResponse.from(repository.save(e));
    }

    @Transactional
    public ItemGroupResponse update(UUID id, ItemGroupRequest req) {
        ItemGroup e = getById(id);
        e.setCode(req.code().toUpperCase());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
        return ItemGroupResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    private ItemGroup getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", id));
    }
}
