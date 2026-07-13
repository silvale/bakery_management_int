package com.bakery.api.production.service;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.production.dto.ProductionGroupRequest;
import com.bakery.api.production.dto.ProductionGroupResponse;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.api.production.entity.ProductionGroupItem;
import com.bakery.api.production.repository.ItemGroupRepository;
import com.bakery.api.production.repository.ProductionGroupRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductionGroupService {

    private final ProductionGroupRepository repository;
    private final ItemGroupRepository itemGroupRepository;
    private final ItemLookupRepository itemRepository;

    @Transactional(readOnly = true)
    public List<ProductionGroupResponse> findAll() {
        return repository.findByActiveTrue().stream()
                .map(ProductionGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionGroupResponse> findByItemGroup(UUID itemGroupId) {
        return repository.findByItemGroupIdOrderByCodeAsc(itemGroupId).stream()
                .map(ProductionGroupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionGroupResponse findById(UUID id) {
        return ProductionGroupResponse.from(getById(id));
    }

    @Transactional
    public ProductionGroupResponse create(ProductionGroupRequest req) {
        ProductionGroup e = new ProductionGroup();
        apply(e, req);
        return ProductionGroupResponse.from(repository.save(e));
    }

    @Transactional
    public ProductionGroupResponse update(UUID id, ProductionGroupRequest req) {
        ProductionGroup e = getById(id);
        e.getItems().clear();
        apply(e, req);
        return ProductionGroupResponse.from(repository.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        ProductionGroup e = getById(id);
        e.setActive(false);
        repository.save(e);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void apply(ProductionGroup e, ProductionGroupRequest req) {
        e.setCode(req.code().toUpperCase());
        e.setName(req.name());
        e.setGroupType(req.groupType());
        e.setTargetWeekday(req.targetWeekday());
        e.setTargetWeekend(req.targetWeekend());
        e.setBatchWeightGrams(req.batchWeightGrams());
        e.setNote(req.note());

        if (req.itemGroupId() != null) {
            ItemGroup ig = itemGroupRepository.findById(req.itemGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", req.itemGroupId()));
            e.setItemGroup(ig);
        }

        if (req.items() != null) {
            for (ProductionGroupRequest.ProductionGroupItemRequest ir : req.items()) {
                Item item = itemRepository.findById(ir.itemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Item", ir.itemId()));
                ProductionGroupItem gi = new ProductionGroupItem();
                gi.setGroup(e);
                gi.setItem(item);
                gi.setGramsPerUnit(ir.gramsPerUnit());
                gi.setSortOrder(ir.sortOrder());
                e.getItems().add(gi);
            }
        }
    }

    private ProductionGroup getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionGroup", id));
    }
}
