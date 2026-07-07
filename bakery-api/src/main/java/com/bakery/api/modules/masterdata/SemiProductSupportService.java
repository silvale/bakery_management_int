package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.framework.exceptions.AdminValidationException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.dtos.SemiProductFilter;
import com.bakery.api.modules.masterdata.dtos.SemiProductRequest;
import com.bakery.api.modules.masterdata.dtos.SemiProductResponse;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import com.bakery.api.modules.masterdata.repositories.SemiProductRepository;
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
public class SemiProductSupportService
        extends AdminEntitySupportService<SemiProductRequest, SemiProductResponse, SemiProduct> {

    private final SemiProductRepository semiProductRepository;

    @Override
    public String entityType() {
        return "SemiProduct";
    }

    @Override
    protected Class<SemiProductResponse> responseClass() {
        return SemiProductResponse.class;
    }

    @Override
    protected JpaRepository<SemiProduct, UUID> repository() {
        return semiProductRepository;
    }

    @Override
    public SemiProductResponse toResponse(SemiProduct e) {
        SemiProductResponse r = new SemiProductResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setType(e.getType());
        r.setTotalYieldKg(e.getTotalYieldKg());
        r.setIsActive(e.getIsActive());
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
    protected SemiProduct toEntity(SemiProductRequest req) {
        return SemiProduct.builder()
                .code(req.getCode().trim().toUpperCase())
                .name(req.getName().trim())
                .type(req.getType())
                .totalYieldKg(req.getTotalYieldKg())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
    }

    @Override
    protected void updateEntity(SemiProduct entity, SemiProductRequest req) {
        entity.setName(req.getName().trim());
        entity.setType(req.getType());
        entity.setTotalYieldKg(req.getTotalYieldKg());
        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());
        // code không cho phép update
    }

    @Override
    protected void beforeCreate(SemiProductRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (semiProductRepository.existsByCode(code)) {
            throw new AdminValidationException("Code '" + code + "' đã tồn tại");
        }
    }

    @Override
    protected Specification<SemiProduct> buildSpecification(AdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEntityStatus() != null) {
                predicates.add(cb.equal(root.get("entityStatus"), filter.getEntityStatus()));
            } else {
                predicates.add(cb.equal(root.get("entityStatus"), EntityStatus.ACTIVE));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String like = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)
                ));
            }

            // Lọc theo type (PHOI | NHAN) nếu filter là SemiProductFilter
            if (filter instanceof SemiProductFilter sf && sf.getType() != null) {
                predicates.add(cb.equal(root.get("type"), sf.getType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
