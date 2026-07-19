package com.bakery.api.master.service;

import com.bakery.api.master.dto.SupplierRequest;
import com.bakery.api.master.dto.SupplierResponse;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierService extends AbstractBakeryAdminService<Supplier, SupplierRequest, SupplierResponse> {

    private final SupplierRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Supplier> getRepository() { return repository; }
    @Override public Class<Supplier> getEntityClass() { return Supplier.class; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Supplier"; }

    @Override
    protected Supplier toEntity(SupplierRequest req) {
        Supplier e = new Supplier();
        e.setCode(req.code());
        e.setName(req.name());
        e.setContactName(req.contactName());
        e.setPhone(req.phone());
        e.setEmail(req.email());
        e.setAddress(req.address());
        return e;
    }

    @Override
    protected void applyUpdate(Supplier e, SupplierRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setContactName(req.contactName());
        e.setPhone(req.phone());
        e.setEmail(req.email());
        e.setAddress(req.address());
    }

    @Override
    protected SupplierResponse toResponse(Supplier e) {
        SupplierResponse r = new SupplierResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setContactName(e.getContactName());
        r.setPhone(e.getPhone());
        r.setEmail(e.getEmail());
        r.setAddress(e.getAddress());
        return r;
    }
}
