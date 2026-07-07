package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.framework.exceptions.AdminValidationException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.dtos.BranchFilter;
import com.bakery.api.modules.masterdata.dtos.BranchRequest;
import com.bakery.api.modules.masterdata.dtos.BranchResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
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
public class BranchSupportService
        extends AdminEntitySupportService<BranchRequest, BranchResponse, Branch> {

    private final BranchRepository branchRepository;

    @Override
    public String entityType() { return "Branch"; }

    @Override
    protected Class<BranchResponse> responseClass() { return BranchResponse.class; }

    @Override
    protected JpaRepository<Branch, UUID> repository() { return branchRepository; }

    @Override
    public BranchResponse toResponse(Branch e) {
        BranchResponse r = new BranchResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setAddress(e.getAddress());
        r.setIsMain(e.getIsMain());
        r.setIsActive(e.getIsActive());
        r.setBranchType(e.getBranchType());
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
    protected Branch toEntity(BranchRequest req) {
        return Branch.builder()
                .code(req.getCode().trim().toUpperCase())
                .name(req.getName().trim())
                .address(req.getAddress())
                .isMain(req.getIsMain() != null ? req.getIsMain() : false)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .branchType(req.getBranchType())
                .build();
    }

    @Override
    protected void updateEntity(Branch entity, BranchRequest req) {
        entity.setName(req.getName().trim());
        entity.setAddress(req.getAddress());
        if (req.getIsMain() != null)   entity.setIsMain(req.getIsMain());
        if (req.getIsActive() != null) entity.setIsActive(req.getIsActive());
        entity.setBranchType(req.getBranchType());
        // code không cho update
    }

    @Override
    protected void beforeCreate(BranchRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (branchRepository.existsByCode(code)) {
            throw new AdminValidationException("Code '" + code + "' đã tồn tại");
        }
    }

    @Override
    protected Specification<Branch> buildSpecification(AdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            EntityStatus status = filter.getEntityStatus() != null
                    ? filter.getEntityStatus() : EntityStatus.ACTIVE;
            predicates.add(cb.equal(root.get("entityStatus"), status));

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String like = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)
                ));
            }

            // BranchFilter-specific: lọc theo branchType
            if (filter instanceof BranchFilter bf && bf.getBranchType() != null) {
                predicates.add(cb.equal(root.get("branchType"), bf.getBranchType()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
