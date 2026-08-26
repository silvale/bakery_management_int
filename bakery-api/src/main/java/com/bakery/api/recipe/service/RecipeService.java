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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý công thức sản phẩm (Recipe).
 *
 * <p>Flow:
 * 1. Tạo recipe → PENDING_APPROVAL, is_active=false, version tự tăng
 * 2. Approve → APPROVED (is_active vẫn false)
 * 3. Activate → is_active=true, tự deactivate recipe cũ cùng SP,
 *    sau đó tự động tính lại {@code unit_cost} cho item liên quan.
 * 4. Clone → bản sao mới PENDING_APPROVAL, parentRecipeId = gốc, version tự tăng
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService
        extends AbstractBakeryAdminService<Recipe, RecipeRequest, RecipeResponse> {

    private final RecipeRepository recipeRepository;
    private final ItemLookupRepository itemRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;
    private final RecipeCostService recipeCostService;
    private final com.bakery.api.master.repository.UnitConversionRepository unitConversionRepository;

    // ── Framework wiring ─────────────────────────────────────────

    @Override protected BaseRepository<Recipe> getRepository() { return recipeRepository; }
    @Override public Class<Recipe> getEntityClass() { return Recipe.class; }
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
        r.setYieldQuantity(recipe.getYieldQuantity());
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
     * Activate recipe: set is_active=true, tự deactivate recipe cũ cùng SP,
     * rồi tính lại {@code unit_cost} cho item liên quan dựa trên công thức mới.
     *
     * <p>Cost recalculation chạy trong cùng transaction nhờ Hibernate AUTO flush:
     * trước khi {@link RecipeCostService#calculate} query DB, Hibernate flush các
     * entity bẩn (recipe cũ=inactive, recipe mới=active) → query thấy đúng trạng thái.
     * Không cần REQUIRES_NEW vì không có lock conflict trên row item.
     *
     * <p>Chỉ recipe đã APPROVED mới được activate.
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

        // Deactivate recipe cũ — dùng entity-level save (không phải bulk @Modifying query)
        // để Envers capture được thay đổi vào recipe_HIS.
        if (recipe.getProduct() != null) {
            recipeRepository.findByProductIdAndActiveTrue(recipe.getProduct().getId())
                    .ifPresent(old -> { old.setActive(false); recipeRepository.save(old); });
        } else {
            recipeRepository.findBySemiProductIdAndActiveTrue(recipe.getSemiProduct().getId())
                    .ifPresent(old -> { old.setActive(false); recipeRepository.save(old); });
        }

        recipe.setActive(true);
        Recipe saved = recipeRepository.save(recipe);

        // ── Tính lại unit_cost sau khi công thức thay đổi ──────────────────────
        // Hibernate AUTO flush sẽ push recipe state vào DB trước khi query bên dưới chạy,
        // nên RecipeCostService sẽ đọc đúng recipe mới active.
        UUID itemId = recipe.getProduct() != null
                ? recipe.getProduct().getId()
                : recipe.getSemiProduct().getId();
        try {
            RecipeCostService.CostResult costResult = recipeCostService.calculate(itemId);
            if (costResult.complete()) {
                Item item = itemRepository.findById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
                item.setUnitCost(costResult.totalCostPerUnit());
                itemRepository.save(item);
                log.info("Cập nhật unit_cost={} cho item '{}' sau khi activate recipe v{}",
                        costResult.totalCostPerUnit(), item.getCode(), saved.getVersion());
            } else {
                log.warn("Không tính đủ cost cho item {} sau khi activate recipe {} " +
                        "(một số NL chưa có giá — unit_cost giữ nguyên).", itemId, recipeId);
            }
        } catch (Exception ex) {
            // Không chặn activate nếu tính cost lỗi (e.g., missing sub-recipe)
            log.warn("Không thể tính lại unit_cost sau khi activate recipe {}: {}",
                    recipeId, ex.getMessage());
        }

        return toResponse(saved);
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

    // ── Public mapping ────────────────────────────────────────────

    /** Expose mapping cho ItemService khi nhúng recipe vào ItemResponse. */
    public RecipeResponse mapToResponse(Recipe recipe) {
        return toResponse(recipe);
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
                throw new IllegalArgumentException("\"" + item.getName() + "\" không phải sản phẩm (PRODUCT).");
            }
            recipe.setProduct(p);
        } else {
            Item item = itemRepository.findById(req.semiProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("SemiProduct", req.semiProductId()));
            if (!(item instanceof SemiProduct sp)) {
                throw new IllegalArgumentException("\"" + item.getName() + "\" không phải bán thành phẩm (SEMI_PRODUCT).");
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
    /**
     * Delegate tới repository để lấy danh sách UNIT_MISMATCH trong active recipe.
     * Kết quả là Object[] với 7 cột:
     *   [0] product_code, [1] product_name, [2] product_type,
     *   [3] ingredient_code, [4] ingredient_name, [5] ingredient_unit, [6] recipe_unit
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<Object[]> findUnitMismatchIssues() {
        return recipeRepository.findUnitMismatchIssues();
    }

    /**
     * Tự động fill yieldQuantity = tổng KG nguyên liệu cho tất cả active recipe
     * chưa có yieldQuantity (null hoặc <= 0).
     * POST /api/v1/recipes/yield/auto-fill
     * Trả về: { total, filled, alreadySet, noKgIngredients, details[] }
     */
    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Object> autoFillYieldQuantity() {
        java.util.List<com.bakery.api.recipe.entity.Recipe> recipes =
                recipeRepository.findByActiveTrue();

        int filled = 0, alreadySet = 0, noKg = 0;
        java.util.List<java.util.Map<String, Object>> details = new java.util.ArrayList<>();

        for (com.bakery.api.recipe.entity.Recipe recipe : recipes) {
            String itemCode = recipe.getProduct() != null
                    ? recipe.getProduct().getCode()
                    : (recipe.getSemiProduct() != null ? recipe.getSemiProduct().getCode() : "?");
            String itemName = recipe.getProduct() != null
                    ? recipe.getProduct().getName()
                    : (recipe.getSemiProduct() != null ? recipe.getSemiProduct().getName() : "?");

            if (recipe.getYieldQuantity() != null
                    && recipe.getYieldQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                alreadySet++;
                continue;
            }

            java.math.BigDecimal kgSum = recipe.getLines().stream()
                    .filter(l -> l.getUnit() != null && l.getQuantity() != null)
                    .map(l -> {
                        String unit = l.getUnit();
                        java.math.BigDecimal qty = l.getQuantity();
                        if (unit.equalsIgnoreCase("KG")) return qty;
                        // Tra unit_conversion để quy đổi về KG
                        return unitConversionRepository.findConversion(unit, "KG")
                                .map(uc -> qty.multiply(uc.getFactor()))
                                .orElse(java.math.BigDecimal.ZERO);
                    })
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            if (kgSum.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                noKg++;
                java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
                d.put("itemCode", itemCode); d.put("itemName", itemName);
                d.put("status", "NO_KG_INGREDIENTS");
                details.add(d);
                continue;
            }

            recipe.setYieldQuantity(kgSum.setScale(4, java.math.RoundingMode.HALF_UP));
            recipeRepository.save(recipe);
            filled++;
            java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
            d.put("itemCode", itemCode); d.put("itemName", itemName);
            d.put("status", "FILLED");
            d.put("yieldQuantity", kgSum.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString() + " KG");
            details.add(d);
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", recipes.size());
        result.put("filled", filled);
        result.put("alreadySet", alreadySet);
        result.put("noKgIngredients", noKg);
        result.put("details", details);
        return result;
    }

    /**
     * Trả về danh sách sản phẩm / bán thành phẩm (active recipe) có dùng đến item này.
     * GET /api/v1/recipes/usage/{itemId}
     * Mỗi phần tử: { productCode, productName, productType, recipeVersion, quantity, unit }
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> findUsageByItem(UUID itemId) {
        java.util.List<com.bakery.api.recipe.entity.Recipe> recipes =
                recipeRepository.findActiveRecipesUsingItem(itemId);

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.bakery.api.recipe.entity.Recipe recipe : recipes) {
            // Lấy thông tin sản phẩm đầu ra
            Item parent = recipe.getProduct() != null
                    ? recipe.getProduct()
                    : recipe.getSemiProduct();
            if (parent == null) continue;

            String parentType = recipe.getProduct() != null ? "PRODUCT" : "SEMI_PRODUCT";

            // Tìm recipe line cụ thể chứa itemId để lấy qty & unit
            recipe.getLines().stream()
                    .filter(l -> itemId.equals(l.getItem() != null ? l.getItem().getId() : null))
                    .forEach(line -> {
                        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("productId",      parent.getId());
                        row.put("productCode",    parent.getCode());
                        row.put("productName",    parent.getName());
                        row.put("productType",    parentType);
                        row.put("recipeVersion",  recipe.getVersion());
                        row.put("quantity",       line.getQuantity());
                        row.put("unit",           line.getUnit());
                        result.add(row);
                    });
        }
        return result;
    }

}
