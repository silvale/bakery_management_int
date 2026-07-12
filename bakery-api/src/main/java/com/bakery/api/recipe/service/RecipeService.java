/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.service;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.recipe.dto.RecipeLineRequest;
import com.bakery.api.recipe.dto.RecipeLineResponse;
import com.bakery.api.recipe.dto.RecipeRequest;
import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý công thức sản phẩm (Recipe).
 *
 * <p>Flow:
 * 1. Tạo recipe → PENDING_APPROVAL, is_active=false, version tự tăng
 * 2. Approve → APPROVED (is_active vẫn false)
 * 3. Activate → is_active=true, tự deactivate recipe cũ cùng SP
 * 4. Clone → bản sao mới PENDING_APPROVAL, parentRecipeId = gốc, version tự tăng
 */
@Service
@RequiredArgsConstructor
public class RecipeService
        extends AbstractBakeryAdminService<Recipe, RecipeRequest, RecipeResponse> {

    private final RecipeRepository recipeRepository;
    private final ItemLookupRepository itemRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    // ── Framework wiring ─────────────────────────────────────────

    @Override protected BaseRepository<Recipe> getRepository() { return recipeRepository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Recipe"; }

    // ── Mapping ──────────────────────────────────────────────────

    @Override
    protected Recipe toEntity(RecipeRequest req) {
        validateTarget(req);
        Recipe recipe = new Recipe();
        applyTarget(recipe, req);
        recipe.setNote(req.note());
        recipe.setActive(false);
        applyLines(recipe, req.lines());
        return recipe;
    }

    @Override
    protected void applyUpdate(Recipe recipe, RecipeRequest req) {
        if (recipe.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Không thể sửa recipe đã APPROVED. Hãy clone và tạo phiên bản mới.");
        }
        recipe.setNote(req.note());
        recipe.getLines().clear();
        applyLines(recipe, req.lines());
    }

    @Override
    protected RecipeResponse toResponse(Recipe recipe) {
        RecipeResponse r = new RecipeResponse();
        r.applyFrom(recipe);
        r.setVersion(recipe.getVersion());
        r.setActive(recipe.isActive());
        r.setNote(recipe.getNote());
        if (recipe.getParentRecipe() != null) {
            r.setParentRecipeId(recipe.getParentRecipe().getId());
        }
        if (recipe.getProduct() != null) {
            r.setProduct(new ReferenceValue(recipe.getProduct().getCode(), recipe.getProduct().getName()));
        }
        if (recipe.getSemiProduct() != null) {
            r.setSemiProduct(new ReferenceValue(recipe.getSemiProduct().getCode(), recipe.getSemiProduct().getName()));
        }
        r.setLines(recipe.getLines().stream().map(this::toLineResponse).toList());
        return r;
    }

    private RecipeLineResponse toLineResponse(RecipeLine line) {
        RecipeLineResponse lr = new RecipeLineResponse();
        lr.setId(line.getId());
        lr.setQuantity(line.getQuantity());
        lr.setUnit(line.getUnit());
        lr.setSortOrder(line.getSortOrder());
        if (line.getItem() != null) {
            lr.setItem(new ReferenceValue(line.getItem().getCode(), line.getItem().getName()));
            lr.setItemType(itemType(line.getItem()));
        }
        return lr;
    }

    // ── Lifecycle hooks ──────────────────────────────────────────

    /** Auto-increment version trước khi tạo. */
    @Override
    protected void beforeCreate(Recipe recipe) {
        int nextVersion;
        if (recipe.getProduct() != null) {
            nextVersion = recipeRepository.maxVersionByProduct(recipe.getProduct().getId()) + 1;
        } else {
            nextVersion = recipeRepository.maxVersionBySemiProduct(recipe.getSemiProduct().getId()) + 1;
        }
        recipe.setVersion(nextVersion);
    }

    // ── Business actions ─────────────────────────────────────────

    /**
     * Activate recipe: set is_active=true và tự deactivate recipe cũ cùng SP.
     * Chỉ recipe đã APPROVED mới được activate.
     */
    @Transactional
    public RecipeResponse activate(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe", recipeId));

        if (recipe.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Chỉ recipe đã APPROVED mới có thể activate.");
        }
        if (recipe.isActive()) {
            return toResponse(recipe); // idempotent
        }

        // Deactivate recipe cũ
        if (recipe.getProduct() != null) {
            recipeRepository.deactivateAllByProduct(recipe.getProduct().getId());
        } else {
            recipeRepository.deactivateAllBySemiProduct(recipe.getSemiProduct().getId());
        }

        recipe.setActive(true);
        return toResponse(recipeRepository.save(recipe));
    }

    /**
     * Clone recipe: tạo bản sao với tất cả lines, parentRecipeId = gốc.
     * Bản clone luôn bắt đầu từ PENDING_APPROVAL, is_active=false, version mới.
     */
    @Transactional
    public RecipeResponse clone(UUID sourceId) {
        Recipe source = recipeRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe", sourceId));

        Recipe clone = new Recipe();
        clone.setProduct(source.getProduct());
        clone.setSemiProduct(source.getSemiProduct());
        clone.setNote(source.getNote());
        clone.setActive(false);
        clone.setParentRecipe(source);
        clone.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);

        // version tự tăng trong beforeCreate
        int nextVersion;
        if (source.getProduct() != null) {
            nextVersion = recipeRepository.maxVersionByProduct(source.getProduct().getId()) + 1;
        } else {
            nextVersion = recipeRepository.maxVersionBySemiProduct(source.getSemiProduct().getId()) + 1;
        }
        clone.setVersion(nextVersion);
        clone.setCreatedBy(actorResolver.currentUserId());

        // Copy lines
        for (RecipeLine srcLine : source.getLines()) {
            RecipeLine newLine = new RecipeLine();
            newLine.setRecipe(clone);
            newLine.setItem(srcLine.getItem());
            newLine.setQuantity(srcLine.getQuantity());
            newLine.setUnit(srcLine.getUnit());
            newLine.setSortOrder(srcLine.getSortOrder());
            clone.getLines().add(newLine);
        }

        return toResponse(recipeRepository.save(clone));
    }

    /**
     * Tất cả recipe (đã approved / chưa approved) của 1 product.
     */
    public List<RecipeResponse> findByProduct(UUID productId) {
        return recipeRepository.findByProductId(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Tất cả recipe của 1 semi-product.
     */
    public List<RecipeResponse> findBySemiProduct(UUID semiProductId) {
        return recipeRepository.findBySemiProductId(semiProductId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────

    private void validateTarget(RecipeRequest req) {
        boolean hasProduct = req.productId() != null;
        boolean hasSemi = req.semiProductId() != null;
        if (hasProduct == hasSemi) {
            throw new IllegalArgumentException(
                    "Phải cung cấp đúng một trong hai: productId hoặc semiProductId.");
        }
    }

    private void applyTarget(Recipe recipe, RecipeRequest req) {
        if (req.productId() != null) {
            Item item = itemRepository.findById(req.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", req.productId()));
            if (!(item instanceof Product p)) {
                throw new IllegalArgumentException("itemId " + req.productId() + " không phải PRODUCT.");
            }
            recipe.setProduct(p);
        } else {
            Item item = itemRepository.findById(req.semiProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("SemiProduct", req.semiProductId()));
            if (!(item instanceof SemiProduct sp)) {
                throw new IllegalArgumentException("itemId " + req.semiProductId() + " không phải SEMI_PRODUCT.");
            }
            recipe.setSemiProduct(sp);
        }
    }

    private void applyLines(Recipe recipe, List<RecipeLineRequest> lineRequests) {
        for (int i = 0; i < lineRequests.size(); i++) {
            RecipeLineRequest lr = lineRequests.get(i);
            Item ingredient = itemRepository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId()));
            RecipeLine line = new RecipeLine();
            line.setRecipe(recipe);
            line.setItem(ingredient);
            line.setQuantity(lr.quantity());
            line.setUnit(lr.unit());
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            recipe.getLines().add(line);
        }
    }

    /**
     * Xác định item_type an toàn với Hibernate proxy (LAZY loading).
     * Dùng instanceof thay getClass() để tránh proxy class name.
     */
    private String itemType(Item item) {
        if (item instanceof Product) return "PRODUCT";
        if (item instanceof SemiProduct) return "SEMI_PRODUCT";
        return "INGREDIENT";
    }
}
