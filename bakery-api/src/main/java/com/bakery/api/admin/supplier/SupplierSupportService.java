package com.bakery.api.admin.supplier;

import com.bakery.api.admin.supplier.dto.SupplierRequest;
import com.bakery.api.admin.supplier.dto.SupplierResponse;
import com.bakery.api.framework.dto.AdminFilter;
import com.bakery.api.framework.exception.AdminValidationException;
import com.bakery.api.framework.service.AdminEntitySupportService;
import com.bakery.common.entity.Supplier;
import com.bakery.common.entity.enums.EntityStatus;
import com.bakery.common.repository.SupplierRepository;
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
public class SupplierSupportService
        extends AdminEntitySupportService<SupplierRequest, SupplierResponse, Supplier> {

    private final SupplierRepository supplierRepository;

    @Override
    public String entityType() {
        return "Supplier";
    }

    @Override
    protected JpaRepository<Supplier, UUID> repository() {
        return supplierRepository;
    }

    @Override
    public SupplierResponse toResponse(Supplier e) {
        SupplierResponse r = new SupplierResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setAddress(e.getAddress());
        r.setPhone(e.getPhone());
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
    protected Supplier toEntity(SupplierRequest req) {
        return Supplier.builder()
                .code(req.getCode().trim().toUpperCase())
                .name(req.getName().trim())
                .address(req.getAddress())
                .phone(req.getPhone())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
    }

    @Override
    protected void updateEntity(Supplier entity, SupplierRequest req) {
        // code không cho phép update
        entity.setName(req.getName().trim());
        entity.setAddress(req.getAddress());
        entity.setPhone(req.getPhone());
        if (req.getIsActive() != null) {
            entity.setIsActive(req.getIsActive());
        }
    }

    @Override
    protected void beforeCreate(SupplierRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (supplierRepository.existsByCode(code)) {
            throw new AdminValidationException("Code '" + code + "' đã tồn tại");
        }
    }

    @Override
    protected Specification<Supplier> buildSpecification(AdminFilter filter) {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
