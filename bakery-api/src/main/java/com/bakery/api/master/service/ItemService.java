/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.dto.IngredientRequest;
import com.bakery.api.master.dto.IngredientResponse;
import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.dto.ProductRequest;
import com.bakery.api.master.dto.ProductResponse;
import com.bakery.api.master.dto.SemiProductRequest;
import com.bakery.api.master.dto.SemiProductResponse;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.dto.PageResult;
import com.bakery.framework.dto.RejectRequest;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.util.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

/**
 * Facade service cho /api/v1/items.
 *
 * <p>READ: query trực tiếp từ ItemLookupRepository với Specification lọc theo
 * discriminator type (item_type) khi có param ?itemType=...
 *
 * <p>WRITE: route tới IngredientService / SemiProductService / ProductService
 * dựa vào itemType trong request.
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemLookupRepository itemRepository;
    private final IngredientService ingredientService;
    private final SemiProductService semiProductService;
    private final ProductService productService;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;

    // ── READ ─────────────────────────────────────────────────────────────────

    public PageResult<ItemResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        Specification<Item> spec = buildSpec(params);
        Page<ItemResponse> page = itemRepository.findAll(spec, pageable).map(this::toResponse);
        return PageResult.of(page);
    }

    public List<ItemResponse> findAll() {
        return itemRepository.findAll().stream().map(this::toResponse).toList();
    }

    public Optional<ItemResponse> findById(UUID id) {
        return itemRepository.findById(id).map(this::toResponse);
    }

    // ── WRITE ────────────────────────────────────────────────────────────────

    public ItemResponse create(ItemRequest req) {
        return switch (req.itemType().toUpperCase()) {
            case "INGREDIENT" -> fromIngredient(ingredientService.create(toIngredientReq(req)));
            case "SEMI_PRODUCT" -> fromSemiProduct(semiProductService.create(toSemiProductReq(req)));
            case "PRODUCT" -> fromProduct(productService.create(toProductReq(req)));
            default -> throw new IllegalArgumentException("Unknown itemType: " + req.itemType());
        };
    }

    public ItemResponse update(UUID id, ItemRequest req) {
        return switch (req.itemType().toUpperCase()) {
            case "INGREDIENT" -> fromIngredient(ingredientService.update(id, toIngredientReq(req)));
            case "SEMI_PRODUCT" -> fromSemiProduct(semiProductService.update(id, toSemiProductReq(req)));
            case "PRODUCT" -> fromProduct(productService.update(id, toProductReq(req)));
            default -> throw new IllegalArgumentException("Unknown itemType: " + req.itemType());
        };
    }

    public void delete(UUID id) {
        Item item = loadItem(id);
        if (item instanceof Ingredient) ingredientService.delete(id);
        else if (item instanceof SemiProduct) semiProductService.delete(id);
        else if (item instanceof Product) productService.delete(id);
    }

    public ItemResponse approve(UUID id) {
        Item item = loadItem(id);
        if (item instanceof Ingredient) return fromIngredient(ingredientService.approve(id));
        if (item instanceof SemiProduct) return fromSemiProduct(semiProductService.approve(id));
        if (item instanceof Product) return fromProduct(productService.approve(id));
        throw new IllegalStateException("Unknown item type for id: " + id);
    }

    public ItemResponse reject(UUID id, String reason) {
        Item item = loadItem(id);
        if (item instanceof Ingredient) return fromIngredient(ingredientService.reject(id, reason));
        if (item instanceof SemiProduct) return fromSemiProduct(semiProductService.reject(id, reason));
        if (item instanceof Product) return fromProduct(productService.reject(id, reason));
        throw new IllegalStateException("Unknown item type for id: " + id);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ItemResponse toResponse(Item item) {
        ItemResponse r = new ItemResponse();
        r.applyFrom(item);
        r.setCode(item.getCode());
        r.setName(item.getName());
        r.setUnit(item.getUnit());
        r.setProductCategory(item.getProductCategory());

        if (item instanceof Ingredient ing) {
            r.setItemType("INGREDIENT");
            r.setIngredientType(ing.getIngredientType());
            if (ing.getDefaultSupplier() != null) {
                r.setDefaultSupplier(new ReferenceValue(
                        ing.getDefaultSupplier().getCode(), ing.getDefaultSupplier().getName()));
            }
            // Giá nhập mới nhất
            ingredientService.findLatestPrice(ing.getId()).ifPresent(p -> {
                r.setLastPrice(p.getPrice());
                r.setLastPriceDate(p.getEffectiveDate());
            });
        } else if (item instanceof SemiProduct) {
            r.setItemType("SEMI_PRODUCT");
            // Recipe: active trước, fallback latest
            recipeRepository.findBySemiProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstBySemiProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
        } else if (item instanceof Product prod) {
            r.setItemType("PRODUCT");
            r.setProductType(prod.getProductType());
            r.setSellingPrice(prod.getSellingPrice());
            // Recipe: active trước, fallback latest
            recipeRepository.findByProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstByProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
        }
        return r;
    }

    /** Convert IngredientResponse → ItemResponse */
    private ItemResponse fromIngredient(IngredientResponse src) {
        ItemResponse r = new ItemResponse();
        r.setId(src.getId());
        r.setStatus(src.getStatus());
        r.setApprovalStatus(src.getApprovalStatus());
        r.setCreatedAt(src.getCreatedAt());
        r.setUpdatedAt(src.getUpdatedAt());
        r.setCreatedBy(src.getCreatedBy());
        r.setApprovedAt(src.getApprovedAt());
        r.setApprovedBy(src.getApprovedBy());
        r.setRejectedReason(src.getRejectedReason());
        r.setItemType("INGREDIENT");
        r.setCode(src.getCode());
        r.setName(src.getName());
        r.setUnit(src.getUnit());
        r.setIngredientType(src.getIngredientType());
        r.setDefaultSupplier(src.getDefaultSupplier());
        r.setLastPrice(src.getLastPrice());
        r.setLastPriceDate(src.getLastPriceDate());
        return r;
    }

    /** Convert SemiProductResponse → ItemResponse */
    private ItemResponse fromSemiProduct(SemiProductResponse src) {
        ItemResponse r = new ItemResponse();
        r.setId(src.getId());
        r.setStatus(src.getStatus());
        r.setApprovalStatus(src.getApprovalStatus());
        r.setCreatedAt(src.getCreatedAt());
        r.setUpdatedAt(src.getUpdatedAt());
        r.setCreatedBy(src.getCreatedBy());
        r.setApprovedAt(src.getApprovedAt());
        r.setApprovedBy(src.getApprovedBy());
        r.setRejectedReason(src.getRejectedReason());
        r.setItemType("SEMI_PRODUCT");
        r.setCode(src.getCode());
        r.setName(src.getName());
        r.setUnit(src.getUnit());
        return r;
    }

    /** Convert ProductResponse → ItemResponse */
    private ItemResponse fromProduct(ProductResponse src) {
        ItemResponse r = new ItemResponse();
        r.setId(src.getId());
        r.setStatus(src.getStatus());
        r.setApprovalStatus(src.getApprovalStatus());
        r.setCreatedAt(src.getCreatedAt());
        r.setUpdatedAt(src.getUpdatedAt());
        r.setCreatedBy(src.getCreatedBy());
        r.setApprovedAt(src.getApprovedAt());
        r.setApprovedBy(src.getApprovedBy());
        r.setRejectedReason(src.getRejectedReason());
        r.setItemType("PRODUCT");
        r.setCode(src.getCode());
        r.setName(src.getName());
        r.setUnit(src.getUnit());
        r.setProductType(src.getProductType());
        r.setProductCategory(src.getProductCategory());
        r.setSellingPrice(src.getSellingPrice());
        r.setRecipe(src.getRecipe());
        return r;
    }

    // ── Request converters ────────────────────────────────────────────────────

    private IngredientRequest toIngredientReq(ItemRequest r) {
        return new IngredientRequest(r.code(), r.name(), r.unit(), r.ingredientType(), r.defaultSupplierId());
    }

    private SemiProductRequest toSemiProductReq(ItemRequest r) {
        return new SemiProductRequest(r.code(), r.name(), r.unit());
    }

    private ProductRequest toProductReq(ItemRequest r) {
        return new ProductRequest(r.code(), r.name(), r.unit(),
                r.productType(), r.productCategory(), r.sellingPrice(),
                r.recipeNote(), r.recipeLines());
    }

    // ── Specification ─────────────────────────────────────────────────────────

    /**
     * Xây Specification từ params.
     * Tách riêng param "itemType" để build discriminator predicate (root.type()),
     * còn lại delegate cho SpecificationBuilder.
     */
    private Specification<Item> buildSpec(MultiValueMap<String, String> params) {
        String itemType = params.getFirst("itemType");

        // Remove itemType khỏi params trước khi pass vào SpecificationBuilder
        // (nếu không, SpecificationBuilder sẽ thử root.get("itemType") và bị lỗi)
        MultiValueMap<String, String> rest = new org.springframework.util.LinkedMultiValueMap<>(params);
        rest.remove("itemType");

        Specification<Item> base = SpecificationBuilder.from(rest);

        if (itemType == null || itemType.isBlank()) {
            return base;
        }

        Class<? extends Item> typeClass = switch (itemType.toUpperCase()) {
            case "INGREDIENT" -> Ingredient.class;
            case "SEMI_PRODUCT" -> SemiProduct.class;
            case "PRODUCT" -> Product.class;
            default -> throw new IllegalArgumentException("Unknown itemType: " + itemType);
        };

        Specification<Item> typeSpec = (root, query, cb) -> cb.equal(root.type(), typeClass);
        return base.and(typeSpec);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Item loadItem(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", id));
    }
}
