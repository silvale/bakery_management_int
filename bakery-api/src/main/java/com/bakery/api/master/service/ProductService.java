/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.dto.ProductRequest;
import com.bakery.api.master.dto.ProductResponse;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ProductRepository;
import com.bakery.api.recipe.dto.RecipeLineRequest;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý Product (Sản phẩm).
 *
 * <p>Công thức (Recipe) được bundle vào vòng đời Product — không có màn hình CT riêng:
 * <ul>
 *   <li>CREATE product → nếu có recipeLines thì tạo Recipe (PENDING_APPROVAL) cùng lúc</li>
 *   <li>UPDATE product → nếu có recipeLines thì upsert Recipe (replace lines của bản PENDING)</li>
 *   <li>APPROVE product → tự động APPROVE + ACTIVATE recipe mới nhất đang PENDING</li>
 *   <li>DELETE product → deactivate tất cả recipe đi kèm</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProductService extends AbstractBakeryAdminService<Product, ProductRequest, ProductResponse> {

    private final ProductRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;
    private final RecipeRepository recipeRepository;
    private final ItemLookupRepository itemRepository;
    private final RecipeService recipeService;

    /**
     * ThreadLocal chuyển ProductRequest xuống các lifecycle hook (afterCreate / afterUpdate).
     * Mỗi request đều được clear trong finally để tránh rò rỉ.
     */
    private final ThreadLocal<ProductRequest> currentRequest = new ThreadLocal<>();

    // ── Framework wiring ─────────────────────────────────────────

    @Override protected BaseRepository<Product> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Product"; }

    // ── Override create/update để inject ThreadLocal ──────────────

    @Override
    @Transactional
    public ProductResponse create(ProductRequest req) {
        currentRequest.set(req);
        try {
            return super.create(req);
        } finally {
            currentRequest.remove();
        }
    }

    @Override
    @Transactional
    public ProductResponse update(UUID id, ProductRequest req) {
        currentRequest.set(req);
        try {
            return super.update(id, req);
        } finally {
            currentRequest.remove();
        }
    }

    // ── Mapping ──────────────────────────────────────────────────

    @Override
    protected Product toEntity(ProductRequest req) {
        Product e = new Product();
        applyProductFields(e, req);
        return e;
    }

    @Override
    protected void applyUpdate(Product e, ProductRequest req) {
        applyProductFields(e, req);
    }

    @Override
    protected ProductResponse toResponse(Product e) {
        ProductResponse r = new ProductResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setUnit(e.getUnit());
        r.setProductType(e.getProductType());
        r.setProductCategory(e.getProductCategory());
        r.setSellingPrice(e.getSellingPrice());

        // Ưu tiên active recipe; nếu chưa có thì lấy phiên bản mới nhất
        Optional<Recipe> active = recipeRepository.findByProductIdAndActiveTrue(e.getId());
        if (active.isPresent()) {
            r.setRecipe(recipeService.mapToResponse(active.get()));
        } else {
            recipeRepository.findFirstByProductIdOrderByVersionDesc(e.getId())
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
        }

        return r;
    }

    // ── Lifecycle hooks — Recipe bundling ─────────────────────────

    /**
     * Sau khi Product được tạo thành công, tạo Recipe (nếu có lines trong request).
     * Recipe inherits cùng approvalStatus với Product.
     */
    @Override
    protected void afterCreate(Product product) {
        ProductRequest req = currentRequest.get();
        if (req != null && hasRecipeLines(req)) {
            int version = recipeRepository.maxVersionByProduct(product.getId()) + 1;
            Recipe recipe = buildRecipe(product, req.recipeNote(), req.recipeLines(), version);
            recipe.setApprovalStatus(product.getApprovalStatus());
            recipeRepository.save(recipe);
        }
    }

    /**
     * Sau khi Product được cập nhật, upsert Recipe:
     * - Nếu có bản PENDING_APPROVAL → replace lines
     * - Nếu tất cả đã APPROVED (hoặc chưa có) → tạo phiên bản mới PENDING_APPROVAL
     */
    @Override
    protected void afterUpdate(Product product) {
        ProductRequest req = currentRequest.get();
        if (req == null || !hasRecipeLines(req)) {
            return;
        }

        Optional<Recipe> pendingRecipe = recipeRepository
                .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                        product.getId(), ApprovalStatus.PENDING_APPROVAL);

        if (pendingRecipe.isPresent()) {
            // Cập nhật bản đang chờ duyệt — replace toàn bộ lines
            Recipe recipe = pendingRecipe.get();
            recipe.setNote(req.recipeNote());
            recipe.getLines().clear();
            applyLines(recipe, req.recipeLines());
            recipeRepository.save(recipe);
        } else {
            // Tất cả recipe đều APPROVED (hoặc chưa có) → tạo phiên bản mới
            int version = recipeRepository.maxVersionByProduct(product.getId()) + 1;
            Recipe newVersion = buildRecipe(product, req.recipeNote(), req.recipeLines(), version);
            newVersion.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            recipeRepository.save(newVersion);
        }
    }

    /**
     * Khi Product được APPROVE, tự động APPROVE + ACTIVATE recipe PENDING_APPROVAL mới nhất.
     * Recipe cũ đang active bị deactivate trước.
     */
    @Override
    protected void afterApprove(Product product) {
        recipeRepository
                .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                        product.getId(), ApprovalStatus.PENDING_APPROVAL)
                .ifPresent(recipe -> {
                    recipeRepository.deactivateAllByProduct(product.getId());
                    recipe.setApprovalStatus(ApprovalStatus.APPROVED);
                    recipe.setActive(true);
                    recipeRepository.save(recipe);
                });
    }

    /**
     * Trước khi xóa Product, deactivate tất cả recipe liên quan.
     */
    @Override
    protected void beforeDelete(Product product) {
        List<Recipe> recipes = recipeRepository.findByProductId(product.getId());
        if (!recipes.isEmpty()) {
            recipes.forEach(r -> r.setActive(false));
            recipeRepository.saveAll(recipes);
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private void applyProductFields(Product e, ProductRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setProductType(req.productType());
        e.setProductCategory(req.productCategory());
        e.setSellingPrice(req.sellingPrice());
    }

    private boolean hasRecipeLines(ProductRequest req) {
        return req.recipeLines() != null && !req.recipeLines().isEmpty();
    }

    /**
     * Build Recipe entity từ product + lines. Caller phải set approvalStatus sau khi gọi hàm này.
     */
    private Recipe buildRecipe(Product product, String note, List<RecipeLineRequest> lines, int version) {
        Recipe recipe = new Recipe();
        recipe.setProduct(product);
        recipe.setNote(note);
        recipe.setActive(false);
        recipe.setVersion(version);
        recipe.setCreatedBy(actorResolver.currentUserId());
        applyLines(recipe, lines);
        return recipe;
    }

    private void applyLines(Recipe recipe, List<RecipeLineRequest> lineRequests) {
        for (int i = 0; i < lineRequests.size(); i++) {
            RecipeLineRequest lr = lineRequests.get(i);
            var item = itemRepository.findById(lr.itemId())
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
}
