package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.ProductPriceRequest;
import com.bakery.api.modules.masterdata.dtos.ProductPriceResponse;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.exceptions.AdminEntityNotFoundException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.masterdata.entities.ProductPrice;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.modules.masterdata.repositories.ProductPriceRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductPriceSupportService
        extends AdminEntitySupportService<ProductPriceRequest, ProductPriceResponse, ProductPrice> {

    private final ProductPriceRepository productPriceRepository;
    private final ProductRepository productRepository;

    @Override
    public String entityType() {
        return "ProductPrice";
    }

    @Override
    protected Class<ProductPriceResponse> responseClass() {
        return ProductPriceResponse.class;
    }

    @Override
    protected JpaRepository<ProductPrice, UUID> repository() {
        return productPriceRepository;
    }

    @Override
    public ProductPriceResponse toResponse(ProductPrice e) {
        ProductPriceResponse r = new ProductPriceResponse();
        r.setId(e.getId());
        r.setProductId(e.getProduct().getId());
        r.setProductCode(e.getProduct().getCode());
        r.setProductName(e.getProduct().getName());
        r.setPrice(e.getPrice());
        r.setVersion(e.getVersion());
        r.setEffectiveDate(e.getEffectiveDate());
        r.setNote(e.getNote());
        r.setEntityStatus(e.getEntityStatus());
        r.setCreatedBy(e.getCreatedBy());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedBy(e.getUpdatedBy());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setApprovedBy(e.getApprovedBy());
        r.setApprovedAt(e.getApprovedAt());
        return r;
    }

    @Override
    protected ProductPrice toEntity(ProductPriceRequest req) {
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new AdminEntityNotFoundException(
                        "Product not found: " + req.getProductId()));

        int nextVersion = productPriceRepository.findMaxVersion(req.getProductId()) + 1;

        return ProductPrice.builder()
                .product(product)
                .price(req.getPrice())
                .version(nextVersion)
                .effectiveDate(req.getEffectiveDate())
                .note(req.getNote())
                .build();
    }

    @Override
    protected void updateEntity(ProductPrice entity, ProductPriceRequest req) {
        // Lịch sử giá là bất biến — không cho phép update
        throw new UnsupportedOperationException("Cập nhật giá không được phép. Hãy tạo version giá mới.");
    }

    @Override
    protected Specification<ProductPrice> buildSpecification(AdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEntityStatus() != null) {
                predicates.add(cb.equal(root.get("entityStatus"), filter.getEntityStatus()));
            } else {
                predicates.add(cb.equal(root.get("entityStatus"), EntityStatus.ACTIVE));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String like = "%" + filter.getSearch().trim().toLowerCase() + "%";
                Join<ProductPrice, Product> productJoin = root.join("product");
                predicates.add(cb.or(
                        cb.like(cb.lower(productJoin.get("code")), like),
                        cb.like(cb.lower(productJoin.get("name")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
