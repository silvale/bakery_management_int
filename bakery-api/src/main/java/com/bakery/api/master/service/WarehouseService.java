package com.bakery.api.master.service;

import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.dto.WarehouseResponse;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WarehouseService extends AbstractBakeryAdminService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private final WarehouseRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Warehouse> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Warehouse"; }

    @Override
    protected Warehouse toEntity(WarehouseRequest req) {
        Warehouse e = new Warehouse();
        e.setCode(req.code());
        e.setName(req.name());
        e.setWarehouseType(req.warehouseType());
        e.setAddress(req.address());
        return e;
    }

    @Override
    protected void applyUpdate(Warehouse e, WarehouseRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setWarehouseType(req.warehouseType());
        e.setAddress(req.address());
    }

    @Override
    protected WarehouseResponse toResponse(Warehouse e) {
        WarehouseResponse r = new WarehouseResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setWarehouseType(e.getWarehouseType());
        r.setAddress(e.getAddress());
        return r;
    }
}
