package com.bakery.api.master.service;

import com.bakery.api.master.dto.IngredientRequest;
import com.bakery.api.master.dto.IngredientResponse;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.repository.IngredientRepository;
import com.bakery.api.master.repository.SupplierRepository;
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
public class IngredientService extends AbstractBakeryAdminService<Ingredient, IngredientRequest, IngredientResponse> {

    private final IngredientRepository repository;
    private final SupplierRepository supplierRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Ingredient> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Ingredient"; }

    @Override
    protected Ingredient toEntity(IngredientRequest req) {
        Ingredient e = new Ingredient();
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setIngredientType(req.ingredientType());
        applySupplier(e, req);
        return e;
    }

    @Override
    protected void applyUpdate(Ingredient e, IngredientRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setIngredientType(req.ingredientType());
        applySupplier(e, req);
    }

    private void applySupplier(Ingredient e, IngredientRequest req) {
        if (req.defaultSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(req.defaultSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.defaultSupplierId()));
            e.setDefaultSupplier(supplier);
        } else {
            e.setDefaultSupplier(null);
        }
    }

    @Override
    protected IngredientResponse toResponse(Ingredient e) {
        IngredientResponse r = new IngredientResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setUnit(e.getUnit());
        r.setIngredientType(e.getIngredientType());
        if (e.getDefaultSupplier() != null) {
            r.setDefaultSupplier(new ReferenceValue(
                    e.getDefaultSupplier().getCode(), e.getDefaultSupplier().getName()));
        }
        return r;
    }
}
