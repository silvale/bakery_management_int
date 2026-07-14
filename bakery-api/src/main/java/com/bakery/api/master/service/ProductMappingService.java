package com.bakery.api.master.service;

import com.bakery.api.master.dto.ProductMappingRequest;
import com.bakery.api.master.dto.ProductMappingResponse;
import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductMappingService
        extends AbstractBakeryAdminService<ProductMapping, ProductMappingRequest, ProductMappingResponse> {

    private final ProductMappingRepository repository;
    private final ItemLookupRepository itemRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<ProductMapping> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "ProductMapping"; }

    @Override
    protected ProductMapping toEntity(ProductMappingRequest req) {
        ProductMapping e = new ProductMapping();
        e.setItem(itemRepository.findById(req.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", req.itemId())));
        e.setExCode(req.exCode());
        e.setSellingPrice(req.sellingPrice());
        e.setNote(req.note());
        return e;
    }

    @Override
    protected void applyUpdate(ProductMapping e, ProductMappingRequest req) {
        if (req.itemId() != null) {
            e.setItem(itemRepository.findById(req.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", req.itemId())));
        }
        e.setExCode(req.exCode());
        e.setSellingPrice(req.sellingPrice());
        e.setNote(req.note());
    }

    @Override
    protected ProductMappingResponse toResponse(ProductMapping e) {
        ProductMappingResponse r = new ProductMappingResponse();
        r.applyFrom(e);
        r.setExCode(e.getExCode());
        r.setSellingPrice(e.getSellingPrice());
        r.setNote(e.getNote());
        if (e.getItem() != null) {
            r.setItem(new ReferenceValue(e.getItem().getCode(), e.getItem().getName()));
        }
        return r;
    }
}
