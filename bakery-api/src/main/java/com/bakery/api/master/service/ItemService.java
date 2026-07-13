/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.pricing.repository.IngredientPriceRepository;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.repository.ItemGroupRepository;
import com.bakery.api.recipe.dto.RecipeLineRequest;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import com.bakery.framework.util.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Service thống nhất cho mọi loại Item (INGREDIENT, SEMI_PRODUCT, PRODUCT).
 *
 * <p>Extend {@link AbstractBakeryAdminService} với generic type {@code Item} — đúng pattern
 * của toàn bộ hệ thống. Logic phân loại (toEntity / applyUpdate) dùng switch/instanceof
 * vì mapping là polymorphic và không thể delegate cho MapStruct.
 *
 * <p>Recipe bundling (PRODUCT + SEMI_PRODUCT) được xử lý trong lifecycle hooks:
 * afterCreate → afterUpdate → afterApprove → beforeDelete.
 */
@Service
@RequiredArgsConstructor
public class ItemService extends AbstractBakeryAdminService<Item, ItemRequest, ItemResponse> {

    private final ItemLookupRepository repository;
    private final SupplierRepository supplierRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ItemGroupRepository itemGroupRepository;
    private final BakeryActorResolver actorResolver;
    private final CommandRequestRepository commandRequestRepository;

    /**
     * ThreadLocal chuyển ItemRequest xuống lifecycle hooks (afterCreate / afterUpdate).
     * Luôn được clear trong finally để tránh memory leak.
     */
    private final ThreadLocal<ItemRequest> currentRequest = new ThreadLocal<>();

    // ── Framework wiring ──────────────────────────────────────────────────────

    @Override protected BaseRepository<Item> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Item"; }

    // ── Override findAll — xử lý discriminator spec ───────────────────────────

    /**
     * Hỗ trợ filter theo {@code ?itemType=INGREDIENT|SEMI_PRODUCT|PRODUCT} bằng cách tách
     * param này ra trước, build JPA discriminator predicate ({@code root.type()}), rồi AND
     * với Specification còn lại từ SpecificationBuilder.
     */
    @Override
    public Page<ItemResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        String itemType = params.getFirst("itemType");

        MultiValueMap<String, String> rest = new LinkedMultiValueMap<>(params);
        rest.remove("itemType");

        Specification<Item> spec = SpecificationBuilder.from(rest);

        if (itemType != null && !itemType.isBlank()) {
            Class<? extends Item> typeClass = resolveClass(itemType);
            spec = spec.and((root, query, cb) -> cb.equal(root.type(), typeClass));
        }

        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    // ── Override create/update — inject ThreadLocal ───────────────────────────

    @Override
    @Transactional
    public ItemResponse create(ItemRequest req) {
        currentRequest.set(req);
        try {
            return super.create(req);
        } finally {
            currentRequest.remove();
        }
    }

    @Override
    @Transactional
    public ItemResponse update(UUID id, ItemRequest req) {
        currentRequest.set(req);
        try {
            return super.update(id, req);
        } finally {
            currentRequest.remove();
        }
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Override
    protected Item toEntity(ItemRequest req) {
        return switch (req.itemType().toUpperCase()) {
            case "INGREDIENT" -> {
                Ingredient e = new Ingredient();
                applyCommonFields(e, req);
                e.setIngredientType(req.ingredientType());
                applySupplier(e, req);
                yield e;
            }
            case "SEMI_PRODUCT" -> {
                SemiProduct e = new SemiProduct();
                applyCommonFields(e, req);
                yield e;
            }
            case "PRODUCT" -> {
                Product e = new Product();
                applyCommonFields(e, req);
                applyProductFields(e, req);
                yield e;
            }
            default -> throw new IllegalArgumentException("Unknown itemType: " + req.itemType());
        };
    }

    // ── applyUpdate ───────────────────────────────────────────────────────────

    @Override
    protected void applyUpdate(Item e, ItemRequest req) {
        applyCommonFields(e, req);
        if (e instanceof Ingredient ing) {
            ing.setIngredientType(req.ingredientType());
            applySupplier(ing, req);
        } else if (e instanceof Product prod) {
            applyProductFields(prod, req);
        }
        // SemiProduct: chỉ có common fields, không cần xử lý thêm
    }

    // ── toResponse ────────────────────────────────────────────────────────────

    @Override
    protected ItemResponse toResponse(Item item) {
        ItemResponse r = new ItemResponse();
        r.applyFrom(item);
        r.setCode(item.getCode());
        r.setName(item.getName());
        r.setUnit(item.getUnit());
        if (item.getItemGroup() != null) {
            r.setItemGroup(new ReferenceValue(
                    item.getItemGroup().getCode(), item.getItemGroup().getName()));
        }

        if (item instanceof Ingredient ing) {
            r.setItemType("INGREDIENT");
            r.setIngredientType(ing.getIngredientType());
            if (ing.getDefaultSupplier() != null) {
                r.setDefaultSupplier(new ReferenceValue(
                        ing.getDefaultSupplier().getCode(),
                        ing.getDefaultSupplier().getName()));
            }
            ingredientPriceRepository
                    .findByItemIdOrderByEffectiveDateDesc(ing.getId())
                    .stream().findFirst()
                    .ifPresent(p -> {
                        r.setLastPrice(p.getPrice());
                        r.setLastPriceDate(p.getEffectiveDate());
                    });

        } else if (item instanceof SemiProduct) {
            r.setItemType("SEMI_PRODUCT");
            recipeRepository.findBySemiProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstBySemiProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));

        } else if (item instanceof Product prod) {
            r.setItemType("PRODUCT");
            r.setProductType(prod.getProductType());
            r.setSellingPrice(prod.getSellingPrice());
            recipeRepository.findByProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstByProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
        }

        return r;
    }

    // ── Lifecycle hooks — Recipe bundling ─────────────────────────────────────

    /** Tạo Recipe (nếu có lines) cùng lúc với Item. Recipe inherits approvalStatus của Item. */
    @Override
    protected void afterCreate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null || !hasRecipeLines(req)) return;

        if (item instanceof Product prod) {
            int version = recipeRepository.maxVersionByProduct(prod.getId()) + 1;
            Recipe recipe = buildProductRecipe(prod, req, version);
            recipe.setApprovalStatus(prod.getApprovalStatus());
            recipeRepository.save(recipe);
        } else if (item instanceof SemiProduct sp) {
            int version = recipeRepository.maxVersionBySemiProduct(sp.getId()) + 1;
            Recipe recipe = buildSemiProductRecipe(sp, req, version);
            recipe.setApprovalStatus(sp.getApprovalStatus());
            recipeRepository.save(recipe);
        }
    }

    /**
     * Upsert Recipe khi Item được cập nhật:
     * - Có bản PENDING_APPROVAL → replace lines
     * - Không có → tạo version mới PENDING_APPROVAL
     */
    @Override
    protected void afterUpdate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null || !hasRecipeLines(req)) return;

        if (item instanceof Product prod) {
            recipeRepository
                    .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                            prod.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresentOrElse(
                            recipe -> replaceRecipeLines(recipe, req),
                            () -> {
                                int v = recipeRepository.maxVersionByProduct(prod.getId()) + 1;
                                Recipe r = buildProductRecipe(prod, req, v);
                                r.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                                recipeRepository.save(r);
                            });
        } else if (item instanceof SemiProduct sp) {
            recipeRepository
                    .findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
                            sp.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresentOrElse(
                            recipe -> replaceRecipeLines(recipe, req),
                            () -> {
                                int v = recipeRepository.maxVersionBySemiProduct(sp.getId()) + 1;
                                Recipe r = buildSemiProductRecipe(sp, req, v);
                                r.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                                recipeRepository.save(r);
                            });
        }
    }

    /** Khi Item được APPROVE → tự động APPROVE + ACTIVATE recipe PENDING mới nhất. */
    @Override
    protected void afterApprove(Item item) {
        if (item instanceof Product prod) {
            recipeRepository
                    .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                            prod.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        recipeRepository.deactivateAllByProduct(prod.getId());
                        activateRecipe(recipe);
                    });
        } else if (item instanceof SemiProduct sp) {
            recipeRepository
                    .findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
                            sp.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        recipeRepository.deactivateAllBySemiProduct(sp.getId());
                        activateRecipe(recipe);
                    });
        }
    }

    /** Trước khi xóa Item → deactivate tất cả recipe đi kèm. */
    @Override
    protected void beforeDelete(Item item) {
        List<Recipe> recipes = item instanceof Product prod
                ? recipeRepository.findByProductId(prod.getId())
                : item instanceof SemiProduct sp
                        ? recipeRepository.findBySemiProductId(sp.getId())
                        : List.of();
        if (!recipes.isEmpty()) {
            recipes.forEach(r -> r.setActive(false));
            recipeRepository.saveAll(recipes);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyCommonFields(Item e, ItemRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        if (req.itemGroupId() != null) {
            ItemGroup ig = itemGroupRepository.findById(req.itemGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", req.itemGroupId()));
            e.setItemGroup(ig);
        } else {
            e.setItemGroup(null);
        }
    }

    private void applyProductFields(Product e, ItemRequest req) {
        e.setProductType(req.productType());
        e.setSellingPrice(req.sellingPrice());
    }

    private void applySupplier(Ingredient e, ItemRequest req) {
        if (req.defaultSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(req.defaultSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.defaultSupplierId()));
            e.setDefaultSupplier(supplier);
        } else {
            e.setDefaultSupplier(null);
        }
    }

    private Recipe buildProductRecipe(Product product, ItemRequest req, int version) {
        Recipe recipe = new Recipe();
        recipe.setProduct(product);
        recipe.setNote(req.recipeNote());
        recipe.setActive(false);
        recipe.setVersion(version);
        recipe.setCreatedBy(actorResolver.currentUserId());
        applyRecipeLines(recipe, req.recipeLines());
        return recipe;
    }

    private Recipe buildSemiProductRecipe(SemiProduct sp, ItemRequest req, int version) {
        Recipe recipe = new Recipe();
        recipe.setSemiProduct(sp);
        recipe.setNote(req.recipeNote());
        recipe.setActive(false);
        recipe.setVersion(version);
        recipe.setCreatedBy(actorResolver.currentUserId());
        applyRecipeLines(recipe, req.recipeLines());
        return recipe;
    }

    private void replaceRecipeLines(Recipe recipe, ItemRequest req) {
        recipe.setNote(req.recipeNote());
        recipe.getLines().clear();
        applyRecipeLines(recipe, req.recipeLines());
        recipeRepository.save(recipe);
    }

    private void activateRecipe(Recipe recipe) {
        recipe.setApprovalStatus(ApprovalStatus.APPROVED);
        recipe.setActive(true);
        recipeRepository.save(recipe);
    }

    private void applyRecipeLines(Recipe recipe, List<RecipeLineRequest> lineRequests) {
        for (int i = 0; i < lineRequests.size(); i++) {
            RecipeLineRequest lr = lineRequests.get(i);
            Item item = repository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId()));
            RecipeLine line = new RecipeLine();
            line.setRecipe(recipe);
            line.setItem(item);
            line.setQuantity(lr.quantity());
            line.setUnit(lr.unit());
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            recipe.getLines().add(line);
        }
    }

    private boolean hasRecipeLines(ItemRequest req) {
        return req.recipeLines() != null && !req.recipeLines().isEmpty();
    }

    private Class<? extends Item> resolveClass(String itemType) {
        return switch (itemType.toUpperCase()) {
            case "INGREDIENT"   -> Ingredient.class;
            case "SEMI_PRODUCT" -> SemiProduct.class;
            case "PRODUCT"      -> Product.class;
            default -> throw new IllegalArgumentException("Unknown itemType: " + itemType);
        };
    }
}
