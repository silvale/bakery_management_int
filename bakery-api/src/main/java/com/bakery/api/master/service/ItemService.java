/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.pricing.entity.IngredientPrice;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService extends AbstractBakeryAdminService<Item, ItemRequest, ItemResponse> {

    private final ItemLookupRepository repository;
    private final SupplierRepository supplierRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ItemCostHelper itemCostHelper;
    private final ItemGroupRepository itemGroupRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final BakeryActorResolver actorResolver;
    private final CommandRequestRepository commandRequestRepository;

    /**
     * ThreadLocal chuyển ItemRequest xuống lifecycle hooks (afterCreate / afterUpdate).
     * Luôn được clear trong finally để tránh memory leak.
     */
    private final ThreadLocal<ItemRequest> currentRequest = new ThreadLocal<>();

    /**
     * ThreadLocal giữ price map khi list items — tránh N+1 query ingredient_price.
     * Set trước khi map toResponse(), clear trong finally.
     */
    private final ThreadLocal<Map<UUID, IngredientPrice>> priceMapLocal = new ThreadLocal<>();

    // ── Framework wiring ──────────────────────────────────────────────────────

    @Override protected BaseRepository<Item> getRepository() { return repository; }
    @Override public Class<Item> getEntityClass() { return Item.class; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Item"; }
    @Override protected String entityLabel(com.bakery.api.master.entity.Item e) { return e.getName() != null ? e.getName() : e.getCode(); }

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

        Page<Item> page = repository.findAll(spec, pageable);

        // Batch-fetch latest price cho tất cả ingredients trong page — tránh N+1
        List<UUID> ingredientIds = page.getContent().stream()
                .filter(i -> i instanceof Ingredient)
                .map(Item::getId)
                .toList();
        if (!ingredientIds.isEmpty()) {
            Map<UUID, IngredientPrice> priceMap = ingredientPriceRepository
                    .findLatestByItemIds(ingredientIds)
                    .stream()
                    .collect(Collectors.toMap(p -> p.getItem().getId(), p -> p));
            priceMapLocal.set(priceMap);
        }

        try {
            return page.map(this::toResponse);
        } finally {
            priceMapLocal.remove();
        }
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
        r.setSplittable(item.isSplittable());
        r.setUnitSize(item.getUnitSize());
        r.setUnitCost(item.getUnitCost());
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
            // Dùng pre-fetched price map nếu có (list view), không thì query riêng (detail view)
            Map<UUID, IngredientPrice> priceMap = priceMapLocal.get();
            Optional<IngredientPrice> latestPrice = priceMap != null
                    ? Optional.ofNullable(priceMap.get(ing.getId()))
                    : ingredientPriceRepository.findByItemIdOrderByEffectiveDateDesc(ing.getId())
                            .stream().findFirst();
            latestPrice.ifPresent(p -> {
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
            expiryConfigRepository.findByItemId(item.getId())
                    .ifPresent(cfg -> r.setShelfDays(cfg.getShelfDays()));
            recipeRepository.findByProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstByProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
        }

        return r;
    }

    // ── Lifecycle hooks — Recipe bundling ─────────────────────────────────────

    /** Tạo Recipe (nếu có lines) cùng lúc với Item. Recipe inherits approvalStatus của Item.
     * Đồng thời upsert ProductExpiryConfig nếu shelfDays được set. */
    @Override
    protected void afterCreate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null) return;

        if (item instanceof Product prod && req.shelfDays() != null) {
            upsertExpiryConfig(prod, req.shelfDays());
        }

        if (!hasRecipeLines(req)) return;

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
     * Đồng thời upsert ProductExpiryConfig nếu shelfDays được set.
     */
    @Override
    protected void afterUpdate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null) return;

        if (item instanceof Product prod && req.shelfDays() != null) {
            upsertExpiryConfig(prod, req.shelfDays());
        }

        if (!hasRecipeLines(req)) return;

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

    /**
     * Khi Item được APPROVE → tự động APPROVE + ACTIVATE recipe PENDING mới nhất,
     * sau đó tính lại unit_cost trong transaction REQUIRES_NEW riêng biệt.
     *
     * <p>Cost recalculation chạy qua {@link ItemCostHelper} với REQUIRES_NEW để
     * tránh "rollback-only trap": nếu RecipeCostService ném exception, chỉ
     * transaction phụ bị rollback, approve() vẫn commit thành công.
     */
    @Override
    protected void afterApprove(Item item) {
        if (item instanceof Product prod) {
            recipeRepository
                    .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                            prod.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        deactivateCurrentRecipeByProduct(prod.getId());
                        activateRecipe(recipe);
                    });
            // Tính cost trong REQUIRES_NEW riêng (không write → không deadlock với outer tx lock)
            // Sau đó set trực tiếp lên managed entity — outer tx sẽ flush khi commit
            itemCostHelper.calculateCost(item.getId()).ifPresent(cost -> {
                prod.setUnitCost(cost);
                log.debug("Cập nhật unit_cost={} cho Product {} (trong outer tx)", cost, prod.getId());
            });
        } else if (item instanceof SemiProduct sp) {
            recipeRepository
                    .findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
                            sp.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        deactivateCurrentRecipeBySemiProduct(sp.getId());
                        activateRecipe(recipe);
                    });
            itemCostHelper.calculateCost(item.getId()).ifPresent(cost -> {
                sp.setUnitCost(cost);
                log.debug("Cập nhật unit_cost={} cho SemiProduct {} (trong outer tx)", cost, sp.getId());
            });
        } else if (item instanceof Ingredient ing && ing.getUnitCost() != null) {
            // Khi approve ingredient có unitCost → tự động tạo IngredientPrice catalog entry.
            // RecipeCostService đọc ingredient_price (không đọc item.unit_cost trực tiếp),
            // nên phải sync để cost calculation dùng đúng giá mới.
            upsertIngredientPrice(ing);
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

    /**
     * Tạo hoặc cập nhật IngredientPrice catalog cho nguyên liệu.
     * Nếu đã có entry với effective_date = hôm nay → update giá, tránh tạo duplicate.
     * Nếu chưa có → tạo mới với effective_date = today.
     *
     * <p>Được gọi tự động khi approve Ingredient có unitCost,
     * để RecipeCostService (đọc từ ingredient_price) dùng đúng giá mới.
     */
    private void upsertIngredientPrice(Ingredient ing) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.math.BigDecimal price = ing.getUnitCost().setScale(2, java.math.RoundingMode.HALF_UP);
        List<IngredientPrice> existing =
                ingredientPriceRepository.findByItemIdOrderByEffectiveDateDesc(ing.getId());

        // Nếu đã có entry hôm nay → update giá, tránh tạo duplicate cùng ngày
        existing.stream()
                .filter(p -> today.equals(p.getEffectiveDate()))
                .findFirst()
                .ifPresentOrElse(
                        p -> { p.setPrice(price); ingredientPriceRepository.save(p); },
                        () -> {
                            IngredientPrice np = new IngredientPrice();
                            np.setItem(ing);
                            np.setPrice(price);
                            np.setEffectiveDate(today);
                            np.setApprovalStatus(ApprovalStatus.APPROVED);
                            ingredientPriceRepository.save(np);
                        });
    }

    private void applyCommonFields(Item e, ItemRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setSplittable(req.splittable());
        e.setUnitSize(req.unitSize());
        if (req.itemGroupId() != null) {
            ItemGroup ig = itemGroupRepository.findById(req.itemGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", req.itemGroupId()));
            e.setItemGroup(ig);
        } else {
            e.setItemGroup(null);
        }
        // unitCost: chỉ áp dụng cho INGREDIENT (nhập tay).
        // SEMI_PRODUCT / PRODUCT được tính lại trong afterApprove → không ghi đè ở đây.
        if ("INGREDIENT".equalsIgnoreCase(req.itemType())) {
            e.setUnitCost(req.unitCost());
        }
    }

    private void applyProductFields(Product e, ItemRequest req) {
        e.setProductType(req.productType());
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
        // Force dirty Recipe header — nếu chỉ lines thay đổi, Hibernate không detect
        // Recipe là dirty (lines là @NotAudited) → không có UPDATE SQL → Envers bỏ sót.
        // Set updatedAt thủ công để trigger dirty check.
        recipe.setUpdatedAt(java.time.Instant.now());
        recipe.getLines().clear();
        applyRecipeLines(recipe, req.recipeLines());
        recipeRepository.save(recipe);
    }

    private void activateRecipe(Recipe recipe) {
        recipe.setApprovalStatus(ApprovalStatus.APPROVED);
        recipe.setActive(true);
        recipeRepository.save(recipe);
    }

    /**
     * Deactivate recipe đang active của 1 product — dùng entity-level save thay vì
     * bulk @Modifying @Query để Envers capture được thay đổi vào recipe_HIS.
     */
    private void deactivateCurrentRecipeByProduct(UUID productId) {
        recipeRepository.findByProductIdAndActiveTrue(productId)
                .ifPresent(r -> {
                    r.setActive(false);
                    recipeRepository.save(r);
                });
    }

    /**
     * Deactivate recipe đang active của 1 semi-product — tương tự deactivateCurrentRecipeByProduct.
     */
    private void deactivateCurrentRecipeBySemiProduct(UUID semiProductId) {
        recipeRepository.findBySemiProductIdAndActiveTrue(semiProductId)
                .ifPresent(r -> {
                    r.setActive(false);
                    recipeRepository.save(r);
                });
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

    /** Tạo hoặc cập nhật ProductExpiryConfig cho sản phẩm. */
    private void upsertExpiryConfig(Product product, int shelfDays) {
        ProductExpiryConfig cfg = expiryConfigRepository.findByItemId(product.getId())
                .orElseGet(() -> {
                    ProductExpiryConfig c = new ProductExpiryConfig();
                    c.setItem(product);
                    return c;
                });
        cfg.setShelfDays(shelfDays);
        expiryConfigRepository.save(cfg);
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
