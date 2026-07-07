package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.IngredientRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientResponse;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.exceptions.AdminValidationException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IngredientSupportService
        extends AdminEntitySupportService<IngredientRequest, IngredientResponse, Ingredient> {

    private final IngredientRepository ingredientRepository;

    @Override
    public String entityType() {
        return "Ingredient";
    }

    @Override
    protected Class<IngredientResponse> responseClass() {
        return IngredientResponse.class;
    }

    @Override
    protected JpaRepository<Ingredient, UUID> repository() {
        return ingredientRepository;
    }

    @Override
    public IngredientResponse toResponse(Ingredient e) {
        IngredientResponse r = new IngredientResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setBaseUnit(e.getBaseUnit());
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
    protected Ingredient toEntity(IngredientRequest req) {
        return Ingredient.builder()
                .code(req.getCode().trim().toUpperCase())
                .name(req.getName().trim())
                .baseUnit(req.getBaseUnit())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();
    }

    @Override
    protected void updateEntity(Ingredient entity, IngredientRequest req) {
        entity.setName(req.getName().trim());
        entity.setBaseUnit(req.getBaseUnit());
        if (req.getIsActive() != null) {
            entity.setIsActive(req.getIsActive());
        }
        // code không cho phép update
    }

    @Override
    protected void beforeCreate(IngredientRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (ingredientRepository.existsByCode(code)) {
            throw new AdminValidationException("Code '" + code + "' đã tồn tại");
        }
    }

    @Override
    protected Specification<Ingredient> buildSpecification(AdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Lọc entityStatus
            if (filter.getEntityStatus() != null) {
                predicates.add(cb.equal(root.get("entityStatus"), filter.getEntityStatus()));
            } else {
                // Mặc định chỉ hiện ACTIVE
                predicates.add(cb.equal(root.get("entityStatus"), EntityStatus.ACTIVE));
            }

            // Tìm theo code hoặc name
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
