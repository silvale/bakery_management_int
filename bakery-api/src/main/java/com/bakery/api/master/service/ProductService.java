package com.bakery.api.master.service;

import com.bakery.api.master.dto.ProductRequest;
import com.bakery.api.master.dto.ProductResponse;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.repository.ProductRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService extends AbstractBakeryAdminService<Product, ProductRequest, ProductResponse> {

    private final ProductRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Product> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Product"; }

    @Override
    protected Product toEntity(ProductRequest req) {
        Product e = new Product();
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setProductType(req.productType());
        e.setProductCategory(req.productCategory());
        e.setSellingPrice(req.sellingPrice());
        return e;
    }

    @Override
    protected void applyUpdate(Product e, ProductRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setProductType(req.productType());
        e.setProductCategory(req.productCategory());
        e.setSellingPrice(req.sellingPrice());
    }

    @Override
    protected ProductResponse toResponse(Product e) {
        ProductResponse r = new ProductResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setUnit(e.getUnit());
        r.setProductType(e.getProductType());
        r.setProductCategory(e.getProductCategory());
        r.setSellingPrice(e.getSellingPrice());
        return r;
    }
}
