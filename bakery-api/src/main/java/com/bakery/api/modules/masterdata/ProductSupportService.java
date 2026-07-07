package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.*;
import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.exceptions.AdminEntityNotFoundException;
import com.bakery.api.framework.exceptions.AdminValidationException;
import com.bakery.api.framework.services.AdminEntitySupportService;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.production.services.CostCalculationService;
import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.EntityStatus;
import com.bakery.api.framework.repositories.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.ProductPrice;
import com.bakery.api.modules.masterdata.entities.Recipe;
import com.bakery.api.modules.masterdata.entities.RecipeLine;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientPriceRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.masterdata.repositories.ProductMappingRepository;
import com.bakery.api.modules.masterdata.repositories.ProductPriceRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import com.bakery.api.modules.masterdata.repositories.RecipeRepository;
import com.bakery.api.modules.masterdata.repositories.SemiProductRepository;

@Service
@RequiredArgsConstructor
public class ProductSupportService
        extends AdminEntitySupportService<ProductRequest, ProductResponse, Product> {

    private final ProductRepository          productRepository;
    private final IngredientRepository       ingredientRepository;
    private final SemiProductRepository      semiProductRepository;
    private final RecipeRepository           recipeRepository;
    private final ProductMappingRepository   productMappingRepository;
    private final ProductPriceRepository     productPriceRepository;
    private final IngredientPriceRepository  ingredientPriceRepository;
    private final BranchRepository           branchRepository;
    private final CostCalculationService     costCalculationService;

    @Override
    public String entityType() { return "Product"; }

    @Override
    protected Class<ProductResponse> responseClass() { return ProductResponse.class; }

    @Override
    protected JpaRepository<Product, UUID> repository() { return productRepository; }

    // ── toResponse ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ProductResponse toResponse(Product e) {
        ProductResponse r = new ProductResponse();
        r.setId(e.getId());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setProductType(e.getProductType());
        r.setUnit(e.getUnit());
        r.setToleranceRate(e.getToleranceRate());
        r.setIsActive(e.getIsActive());
        r.setEntityStatus(e.getEntityStatus());
        r.setCreatedBy(e.getCreatedBy());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedBy(e.getUpdatedBy());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setApprovedBy(e.getApprovedBy());
        r.setApprovedAt(e.getApprovedAt());

        if (e.getId() != null) {
            // 1. Active recipe + cost từng dòng
            recipeRepository.findActiveWithLines(e.getId())
                    .ifPresent(recipe -> r.setActiveRecipe(toRecipeResponse(recipe)));

            // 2. EX_CODEs + giá bán hiện tại
            r.setExCodes(buildExCodes(e));
        }

        return r;
    }

    // ── EX_CODE + giá ──────────────────────────────────────────

    private List<ProductResponse.ExCodeEntry> buildExCodes(Product product) {
        // Giá bán hiện tại — dùng chung cho tất cả SKU của product này
        BigDecimal currentPrice = productPriceRepository
                .findLatestPrice(product.getId())
                .map(ProductPrice::getPrice)
                .orElse(null);

        String priceUnit = product.getUnit() != null
                ? ("KG".equals(product.getUnit()) ? "VNĐ/kg" : "VNĐ/cái")
                : null;

        return productMappingRepository
                .findAllByProductIdAndIsActiveTrue(product.getId())
                .stream()
                .map(pm -> {
                    ProductResponse.ExCodeEntry entry = new ProductResponse.ExCodeEntry();
                    entry.setSkuCode(pm.getSkuCode());
                    entry.setProductionDay(pm.getProductionDay());
                    entry.setSkuSource(pm.getSkuSource());
                    entry.setCurrentPrice(currentPrice);
                    entry.setPriceUnit(priceUnit);
                    return entry;
                })
                .toList();
    }

    // ── Recipe + cost ───────────────────────────────────────────

    private RecipeResponse toRecipeResponse(Recipe recipe) {
        RecipeResponse r = new RecipeResponse();
        r.setId(recipe.getId());
        r.setVersion(recipe.getVersion());
        r.setIsActive(recipe.getIsActive());
        r.setEffectiveDate(recipe.getEffectiveDate());
        r.setRecipeType(recipe.getRecipeType());
        r.setNote(recipe.getNote());

        List<RecipeLineResponse> lines = recipe.getLines().stream()
                .map(this::toRecipeLineResponse)
                .toList();
        r.setLines(lines);

        // Tổng cost = sum costContribution các dòng (bỏ qua null)
        BigDecimal total = lines.stream()
                .map(RecipeLineResponse::getCostContribution)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setTotalCostPerUnit(total.compareTo(BigDecimal.ZERO) == 0 ? null : total);

        return r;
    }

    private RecipeLineResponse toRecipeLineResponse(RecipeLine line) {
        RecipeLineResponse r = new RecipeLineResponse();
        r.setId(line.getId());
        r.setQuantityGram(line.getQuantityGram());
        r.setLineType(line.getLineType());
        r.setNote(line.getNote());

        if (line.getIngredient() != null) {
            Ingredient ing = line.getIngredient();
            r.setIngredientId(ing.getId());
            r.setIngredientCode(ing.getCode());
            r.setIngredientName(ing.getName());

            // Giá NL hiện tại → cost đóng góp
            ingredientPriceRepository.findLatestPrice(ing.getId()).ifPresent(ip -> {
                BigDecimal pricePerKg = ip.getPricePerKg();
                r.setUnitPricePerKg(pricePerKg);
                // cost = qty_gram / 1000 * price_per_kg
                BigDecimal cost = line.getQuantityGram()
                        .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                        .multiply(pricePerKg)
                        .setScale(2, RoundingMode.HALF_UP);
                r.setCostContribution(cost);
            });

        } else if (line.getSemiProduct() != null) {
            SemiProduct sp = line.getSemiProduct();
            r.setSemiProductId(sp.getId());
            r.setSemiProductCode(sp.getCode());
            r.setSemiProductName(sp.getName());

            // Cost BTP on-the-fly (semi_product_cost đã bị xóa V15)
            branchRepository.findByCode("MAIN").ifPresent(branch -> {
                BigDecimal costPerKg = costCalculationService.calculateCostPerKg(sp, branch);
                if (costPerKg.compareTo(BigDecimal.ZERO) > 0) {
                    r.setUnitPricePerKg(costPerKg);
                    BigDecimal cost = line.getQuantityGram()
                            .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                            .multiply(costPerKg)
                            .setScale(2, RoundingMode.HALF_UP);
                    r.setCostContribution(cost);
                }
            });
        }

        return r;
    }

    // ── toEntity (CREATE) ──────────────────────────────────────

    @Override
    protected Product toEntity(ProductRequest req) {
        Product product = Product.builder()
                .code(req.getCode().trim().toUpperCase())
                .name(req.getName().trim())
                .productType(req.getProductType())
                .unit(req.getUnit().trim().toUpperCase())
                .toleranceRate(req.getToleranceRate() != null ? req.getToleranceRate() : BigDecimal.ZERO)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .build();

        if (req.getRecipe() != null) {
            Recipe recipe = buildRecipe(product, req.getRecipe(), 1);
            product.getRecipes().add(recipe);
        }
        return product;
    }

    // ── updateEntity (UPDATE) ──────────────────────────────────

    @Override
    @Transactional
    protected void updateEntity(Product entity, ProductRequest req) {
        entity.setName(req.getName().trim());
        entity.setProductType(req.getProductType());
        entity.setUnit(req.getUnit().trim().toUpperCase());
        if (req.getToleranceRate() != null) entity.setToleranceRate(req.getToleranceRate());
        if (req.getIsActive() != null)      entity.setIsActive(req.getIsActive());

        if (req.getRecipe() != null) {
            recipeRepository.findByProductIdAndIsActiveTrue(entity.getId())
                    .ifPresent(old -> old.setIsActive(false));
            int nextVersion = recipeRepository.findMaxVersion(entity.getId()) + 1;
            entity.getRecipes().add(buildRecipe(entity, req.getRecipe(), nextVersion));
        }
    }

    // ── Validation ─────────────────────────────────────────────

    @Override
    protected void beforeCreate(ProductRequest req) {
        String code = req.getCode().trim().toUpperCase();
        if (productRepository.existsByCode(code))
            throw new AdminValidationException("Code '" + code + "' đã tồn tại");
        if (req.getRecipe() != null) validateRecipeLines(req.getRecipe().getLines());
    }

    @Override
    protected void beforeUpdate(Product existing, ProductRequest req) {
        if (req.getRecipe() != null) validateRecipeLines(req.getRecipe().getLines());
    }

    // ── Specification ──────────────────────────────────────────

    @Override
    protected Specification<Product> buildSpecification(AdminFilter filter) {
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

    // ── Private helpers ────────────────────────────────────────

    private Recipe buildRecipe(Product product, RecipeRequest recipeReq, int version) {
        Recipe recipe = Recipe.builder()
                .product(product)
                .version(version)
                .isActive(true)
                .effectiveDate(recipeReq.getEffectiveDate())
                .note(recipeReq.getNote())
                .recipeType("BASE")
                .build();
        for (RecipeLineRequest lineReq : recipeReq.getLines()) {
            recipe.getLines().add(buildRecipeLine(recipe, lineReq));
        }
        return recipe;
    }

    private RecipeLine buildRecipeLine(Recipe recipe, RecipeLineRequest lineReq) {
        Ingredient ingredient = null;
        SemiProduct semiProduct = null;
        if (lineReq.getIngredientId() != null) {
            ingredient = ingredientRepository.findById(lineReq.getIngredientId())
                    .orElseThrow(() -> new AdminEntityNotFoundException(
                            "Ingredient not found: " + lineReq.getIngredientId()));
        } else {
            semiProduct = semiProductRepository.findById(lineReq.getSemiProductId())
                    .orElseThrow(() -> new AdminEntityNotFoundException(
                            "SemiProduct not found: " + lineReq.getSemiProductId()));
        }
        return RecipeLine.builder()
                .recipe(recipe)
                .ingredient(ingredient)
                .semiProduct(semiProduct)
                .quantityGram(lineReq.getQuantityGram())
                .lineType(lineReq.getLineType())
                .note(lineReq.getNote())
                .build();
    }

    private void validateRecipeLines(List<RecipeLineRequest> lines) {
        for (int i = 0; i < lines.size(); i++) {
            RecipeLineRequest line = lines.get(i);
            boolean hasIng  = line.getIngredientId() != null;
            boolean hasSemi = line.getSemiProductId() != null;
            if (hasIng == hasSemi)
                throw new AdminValidationException(
                        "Dòng #" + (i + 1) + ": phải có đúng 1 trong 2 — ingredientId HOẶC semiProductId");
        }
    }
}
