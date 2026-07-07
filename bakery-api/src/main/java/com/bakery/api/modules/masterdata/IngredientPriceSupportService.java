package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.IngredientPriceRequest;
import com.bakery.api.modules.masterdata.dtos.IngredientPriceResponse;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.exceptions.AdminEntityNotFoundException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.IngredientPrice;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.modules.masterdata.repositories.IngredientPriceRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
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
public class IngredientPriceSupportService
        extends AdminEntitySupportService<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> {

    private final IngredientPriceRepository ingredientPriceRepository;
    private final IngredientRepository ingredientRepository;

    @Override
    public String entityType() {
        return "IngredientPrice";
    }

    @Override
    protected Class<IngredientPriceResponse> responseClass() {
        return IngredientPriceResponse.class;
    }

    @Override
    protected JpaRepository<IngredientPrice, UUID> repository() {
        return ingredientPriceRepository;
    }

    @Override
    public IngredientPriceResponse toResponse(IngredientPrice e) {
        IngredientPriceResponse r = new IngredientPriceResponse();
        r.setId(e.getId());
        r.setIngredientId(e.getIngredient().getId());
        r.setIngredientCode(e.getIngredient().getCode());
        r.setIngredientName(e.getIngredient().getName());
        r.setPricePerKg(e.getPricePerKg());
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
    protected IngredientPrice toEntity(IngredientPriceRequest req) {
        Ingredient ingredient = ingredientRepository.findById(req.getIngredientId())
                .orElseThrow(() -> new AdminEntityNotFoundException(
                        "Ingredient not found: " + req.getIngredientId()));

        int nextVersion = ingredientPriceRepository.findMaxVersion(req.getIngredientId()) + 1;

        return IngredientPrice.builder()
                .ingredient(ingredient)
                .pricePerKg(req.getPricePerKg())
                .version(nextVersion)
                .effectiveDate(req.getEffectiveDate())
                .note(req.getNote())
                .build();
    }

    @Override
    protected void updateEntity(IngredientPrice entity, IngredientPriceRequest req) {
        // Giá lịch sử không cho phép update — chỉ tạo version mới
        throw new UnsupportedOperationException("Cập nhật giá không được phép. Hãy tạo version giá mới.");
    }

    @Override
    protected Specification<IngredientPrice> buildSpecification(AdminFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEntityStatus() != null) {
                predicates.add(cb.equal(root.get("entityStatus"), filter.getEntityStatus()));
            } else {
                predicates.add(cb.equal(root.get("entityStatus"), EntityStatus.ACTIVE));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String like = "%" + filter.getSearch().trim().toLowerCase() + "%";
                Join<IngredientPrice, Ingredient> ingredientJoin = root.join("ingredient");
                predicates.add(cb.or(
                        cb.like(cb.lower(ingredientJoin.get("code")), like),
                        cb.like(cb.lower(ingredientJoin.get("name")), like)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
