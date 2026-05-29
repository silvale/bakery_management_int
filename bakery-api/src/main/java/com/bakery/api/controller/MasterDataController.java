package com.bakery.api.controller;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.SemiProductType;
import com.bakery.common.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CRUD API cho Master Data:
 *   /master/semi-products  — Bán thành phẩm (Phôi / Nhân)
 *   /master/recipes        — Công thức bánh (versioned)
 *   /master/prefixes       — Product prefix mapping (EX_CODE decoder)
 */
@Slf4j
@RestController
@RequestMapping("/master")
@RequiredArgsConstructor
@Tag(name = "Master Data", description = "Quản lý bán thành phẩm, công thức, và prefix sản phẩm")
public class MasterDataController {

    private final SemiProductRepository    semiProductRepository;
    private final RecipeRepository         recipeRepository;
    private final ProductPrefixRepository  productPrefixRepository;
    private final ProductRepository        productRepository;

    // ═══════════════════════════════════════════════════════════
    //  SEMI PRODUCT
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/semi-products")
    @Operation(summary = "Danh sách bán thành phẩm",
               description = "Lấy tất cả bán thành phẩm đang active. Filter theo type: PHOI | NHAN")
    public ResponseEntity<List<SemiProduct>> listSemiProducts(
            @RequestParam(required = false) SemiProductType type) {

        List<SemiProduct> list = type != null
            ? semiProductRepository.findAllByTypeAndIsActiveTrue(type)
            : semiProductRepository.findAllByIsActiveTrue();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/semi-products/{id}")
    @Operation(summary = "Chi tiết bán thành phẩm")
    public ResponseEntity<SemiProduct> getSemiProduct(@PathVariable UUID id) {
        return semiProductRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/semi-products")
    @Transactional
    @Operation(summary = "Tạo bán thành phẩm mới",
               description = "Tạo Phôi hoặc Nhân mới. Code phải unique.")
    public ResponseEntity<?> createSemiProduct(@RequestBody SemiProductRequest req) {
        if (semiProductRepository.existsByCode(req.code())) {
            return ResponseEntity.badRequest()
                .body("Mã bán thành phẩm đã tồn tại: " + req.code());
        }

        SemiProduct sp = SemiProduct.builder()
            .code(req.code().toUpperCase())
            .name(req.name())
            .type(req.type())
            .totalYieldKg(req.totalYieldKg())
            .isActive(true)
            .build();

        SemiProduct saved = semiProductRepository.save(sp);
        log.info("Tạo SemiProduct: {} | {}", saved.getCode(), saved.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/semi-products/{id}")
    @Transactional
    @Operation(summary = "Cập nhật bán thành phẩm")
    public ResponseEntity<?> updateSemiProduct(
            @PathVariable UUID id,
            @RequestBody SemiProductRequest req) {

        SemiProduct sp = semiProductRepository.findById(id)
            .orElse(null);
        if (sp == null) return ResponseEntity.notFound().build();

        sp.setName(req.name());
        sp.setType(req.type());
        sp.setTotalYieldKg(req.totalYieldKg());
        if (req.isActive() != null) sp.setIsActive(req.isActive());

        semiProductRepository.save(sp);
        log.info("Cập nhật SemiProduct: {}", sp.getCode());
        return ResponseEntity.ok(sp);
    }

    @DeleteMapping("/semi-products/{id}")
    @Transactional
    @Operation(summary = "Vô hiệu hóa bán thành phẩm (soft delete)")
    public ResponseEntity<Void> deactivateSemiProduct(@PathVariable UUID id) {
        return semiProductRepository.findById(id)
            .map(sp -> {
                sp.setIsActive(false);
                semiProductRepository.save(sp);
                log.info("Vô hiệu hóa SemiProduct: {}", sp.getCode());
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════
    //  RECIPE
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/recipes")
    @Operation(summary = "Danh sách công thức",
               description = "Lấy tất cả công thức. Filter theo productId hoặc chỉ active.")
    public ResponseEntity<List<Recipe>> listRecipes(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        List<Recipe> all = recipeRepository.findAll();

        List<Recipe> filtered = all.stream()
            .filter(r -> productId == null || r.getProduct().getId().equals(productId))
            .filter(r -> !activeOnly || Boolean.TRUE.equals(r.getIsActive()))
            .toList();

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/recipes/{id}")
    @Operation(summary = "Chi tiết công thức")
    public ResponseEntity<Recipe> getRecipe(@PathVariable UUID id) {
        return recipeRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recipes")
    @Transactional
    @Operation(summary = "Tạo công thức mới (version mới)",
               description = "Tạo version mới cho sản phẩm. Tự động set version = maxVersion + 1. " +
                             "Deactivate version cũ nếu isActive = true.")
    public ResponseEntity<?> createRecipe(@RequestBody RecipeRequest req) {
        Product product = productRepository.findById(req.productId()).orElse(null);
        if (product == null) {
            return ResponseEntity.badRequest().body("Không tìm thấy sản phẩm: " + req.productId());
        }

        // Tính version tiếp theo
        int nextVersion = recipeRepository.findMaxVersion(req.productId()) + 1;

        // Deactivate version cũ nếu tạo version active
        if (Boolean.TRUE.equals(req.isActive())) {
            recipeRepository.findByProductIdAndIsActiveTrue(req.productId())
                .ifPresent(old -> {
                    old.setIsActive(false);
                    recipeRepository.save(old);
                });
        }

        Recipe recipe = Recipe.builder()
            .product(product)
            .version(nextVersion)
            .isActive(req.isActive() != null ? req.isActive() : true)
            .effectiveDate(req.effectiveDate() != null ? req.effectiveDate() : LocalDate.now())
            .note(req.note())
            .recipeType(req.recipeType() != null ? req.recipeType() : "BASE")
            .build();

        Recipe saved = recipeRepository.save(recipe);
        log.info("Tạo Recipe v{} cho SP: {}", nextVersion, product.getCode());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/recipes/{id}")
    @Transactional
    @Operation(summary = "Cập nhật công thức (note, effectiveDate, isActive)")
    public ResponseEntity<?> updateRecipe(
            @PathVariable UUID id,
            @RequestBody RecipeRequest req) {

        Recipe recipe = recipeRepository.findById(id).orElse(null);
        if (recipe == null) return ResponseEntity.notFound().build();

        if (req.note() != null)          recipe.setNote(req.note());
        if (req.effectiveDate() != null) recipe.setEffectiveDate(req.effectiveDate());
        if (req.isActive() != null) {
            // Nếu activate recipe này → deactivate recipe active khác của cùng product
            if (Boolean.TRUE.equals(req.isActive()) && !Boolean.TRUE.equals(recipe.getIsActive())) {
                recipeRepository.findByProductIdAndIsActiveTrue(recipe.getProduct().getId())
                    .ifPresent(old -> {
                        if (!old.getId().equals(id)) {
                            old.setIsActive(false);
                            recipeRepository.save(old);
                        }
                    });
            }
            recipe.setIsActive(req.isActive());
        }

        recipeRepository.save(recipe);
        log.info("Cập nhật Recipe {} v{}", recipe.getProduct().getCode(), recipe.getVersion());
        return ResponseEntity.ok(recipe);
    }

    // ═══════════════════════════════════════════════════════════
    //  PRODUCT PREFIX
    // ═══════════════════════════════════════════════════════════

    @GetMapping("/prefixes")
    @Operation(summary = "Danh sách prefix sản phẩm",
               description = "Prefix mapping: EX_CODE prefix → IN_CODE product. Sorted by length DESC.")
    public ResponseEntity<List<ProductPrefix>> listPrefixes(
            @RequestParam(defaultValue = "true") boolean activeOnly) {

        List<ProductPrefix> list = activeOnly
            ? productPrefixRepository.findAllActiveOrderByPrefixLengthDesc()
            : productPrefixRepository.findAll();

        return ResponseEntity.ok(list);
    }

    @GetMapping("/prefixes/{id}")
    @Operation(summary = "Chi tiết prefix")
    public ResponseEntity<ProductPrefix> getPrefix(@PathVariable UUID id) {
        return productPrefixRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prefixes")
    @Transactional
    @Operation(summary = "Tạo prefix mới",
               description = "Map EX_CODE prefix (BM, BKST...) → Product (IN_CODE). Prefix phải unique.")
    public ResponseEntity<?> createPrefix(@RequestBody PrefixRequest req) {
        String prefix = req.prefix().trim().toUpperCase();

        if (productPrefixRepository.existsByPrefix(prefix)) {
            return ResponseEntity.badRequest()
                .body("Prefix đã tồn tại: " + prefix);
        }

        Product product = productRepository.findById(req.productId()).orElse(null);
        if (product == null) {
            product = productRepository.findByCode(req.productCode()).orElse(null);
        }
        if (product == null) {
            return ResponseEntity.badRequest()
                .body("Không tìm thấy sản phẩm. Cung cấp productId hoặc productCode hợp lệ.");
        }

        ProductPrefix pp = ProductPrefix.builder()
            .prefix(prefix)
            .description(req.description())
            .product(product)
            .isActive(true)
            .note(req.note())
            .build();

        ProductPrefix saved = productPrefixRepository.save(pp);
        log.info("Tạo ProductPrefix: {} → SP {}", prefix, product.getCode());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/prefixes/{id}")
    @Transactional
    @Operation(summary = "Cập nhật prefix (description, note, isActive, productId)")
    public ResponseEntity<?> updatePrefix(
            @PathVariable UUID id,
            @RequestBody PrefixRequest req) {

        ProductPrefix pp = productPrefixRepository.findById(id).orElse(null);
        if (pp == null) return ResponseEntity.notFound().build();

        if (req.description() != null) pp.setDescription(req.description());
        if (req.note() != null)        pp.setNote(req.note());
        if (req.isActive() != null)    pp.setIsActive(req.isActive());

        if (req.productId() != null) {
            Product product = productRepository.findById(req.productId()).orElse(null);
            if (product == null) return ResponseEntity.badRequest().body("Không tìm thấy sản phẩm");
            pp.setProduct(product);
        } else if (req.productCode() != null) {
            Product product = productRepository.findByCode(req.productCode()).orElse(null);
            if (product == null) return ResponseEntity.badRequest().body("Không tìm thấy SP code: " + req.productCode());
            pp.setProduct(product);
        }

        productPrefixRepository.save(pp);
        log.info("Cập nhật ProductPrefix: {}", pp.getPrefix());
        return ResponseEntity.ok(pp);
    }

    @DeleteMapping("/prefixes/{id}")
    @Transactional
    @Operation(summary = "Vô hiệu hóa prefix (soft delete)")
    public ResponseEntity<Void> deactivatePrefix(@PathVariable UUID id) {
        return productPrefixRepository.findById(id)
            .map(pp -> {
                pp.setIsActive(false);
                productPrefixRepository.save(pp);
                log.info("Vô hiệu hóa ProductPrefix: {}", pp.getPrefix());
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════
    //  Request records
    // ═══════════════════════════════════════════════════════════

    public record SemiProductRequest(
        String         code,
        String         name,
        SemiProductType type,
        BigDecimal     totalYieldKg,
        Boolean        isActive
    ) {}

    public record RecipeRequest(
        UUID      productId,
        LocalDate effectiveDate,
        Boolean   isActive,
        String    note,
        String    recipeType  // BASE | ADDON
    ) {}

    public record PrefixRequest(
        String  prefix,
        String  description,
        UUID    productId,    // one of productId or productCode required
        String  productCode,
        Boolean isActive,
        String  note
    ) {}
}
