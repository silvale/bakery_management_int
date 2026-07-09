package com.bakery.api.master.service;

import com.bakery.api.master.dto.SemiProductRequest;
import com.bakery.api.master.dto.SemiProductResponse;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.repository.SemiProductRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SemiProductService extends AbstractBakeryAdminService<SemiProduct, SemiProductRequest, SemiProductResponse> {

    private final SemiProductRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<SemiProduct> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "SemiProduct"; }

    @Override
    protected SemiProduct toEntity(SemiProductRequest req) {
        SemiProduct e = new SemiProduct();
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        return e;
    }

    @Override
    protected void applyUpdate(SemiProduct e, SemiProductRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
    }

    @Override
    protected SemiProductResponse toResponse(SemiProduct e) {
        SemiProductResponse r = new SemiProductResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setUnit(e.getUnit());
        return r;
    }
}
