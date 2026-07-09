package com.bakery.api.master.service;

import com.bakery.api.master.dto.ProductExpiryConfigRequest;
import com.bakery.api.master.dto.ProductExpiryConfigResponse;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
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
public class ProductExpiryConfigService
        extends AbstractBakeryAdminService<ProductExpiryConfig, ProductExpiryConfigRequest, ProductExpiryConfigResponse> {

    private final ProductExpiryConfigRepository repository;
    private final ItemLookupRepository itemLookupRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<ProductExpiryConfig> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "ProductExpiryConfig"; }
    @Override protected boolean isAutoApprove() { return true; }

    @Override
    protected ProductExpiryConfig toEntity(ProductExpiryConfigRequest req) {
        Item item = itemLookupRepository.findById(req.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", req.itemId()));
        ProductExpiryConfig e = new ProductExpiryConfig();
        e.setItem(item);
        e.setShelfDays(req.shelfDays());
        return e;
    }

    @Override
    protected void applyUpdate(ProductExpiryConfig e, ProductExpiryConfigRequest req) {
        if (!e.getItem().getId().equals(req.itemId())) {
            Item item = itemLookupRepository.findById(req.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", req.itemId()));
            e.setItem(item);
        }
        e.setShelfDays(req.shelfDays());
    }

    @Override
    protected ProductExpiryConfigResponse toResponse(ProductExpiryConfig e) {
        ProductExpiryConfigResponse r = new ProductExpiryConfigResponse();
        r.applyFrom(e);
        r.setItem(new ReferenceValue(e.getItem().getCode(), e.getItem().getName()));
        r.setShelfDays(e.getShelfDays());
        return r;
    }
}
