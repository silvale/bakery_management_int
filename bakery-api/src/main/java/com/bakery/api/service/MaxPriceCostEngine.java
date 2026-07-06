package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tính giá vốn theo MAX PRICE RULE:
 *   Luôn dùng giá CAO NHẤT trong các lô nguyên liệu đang còn trong kho
 *   để tính cost báo cáo — dự phòng rủi ro biến động giá.
 *
 * Phân biệt với FIFO Engine:
 *   FIFO Engine   → quản lý xuất kho vật lý (lô cũ nhất trước)
 *   MaxPriceEngine → tính cost báo cáo lợi nhuận (giá cao nhất)
 *
 * Tuyệt đối không thay đổi giá lịch sử.
 * Chỉ xét các lô đang còn hàng (qty_remaining > 0).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaxPriceCostEngine {

    private final RecipeRepository              recipeRepository;
    private final IngredientStockLotRepository  stockLotRepository;
    private final CostCalculationService        costCalculationService;
    private final ProductMappingRepository      productMappingRepository;

    /**
     * Tính cost/unit cho 1 Master Product theo Max Price Rule.
     * Bao gồm cả base recipe + addon recipe (nếu có SKU mapping).
     *
     * @param product    Master product
     * @param skuCode    SKU POS (nullable — nếu null chỉ tính base recipe)
     * @param reconDate  Ngày tính cost (dùng để lookup recipe version)
     * @param branch     Kho cần lấy giá
     */
    @Transactional(readOnly = true)
    public CostResult calculateMaxPriceCost(
            Product product,
            String skuCode,
            LocalDate reconDate,
            Branch branch) {

        // 1. Load base recipe
        Recipe baseRecipe = recipeRepository
            .findActiveRecipe(product.getId(), reconDate)
            .orElse(null);

        if (baseRecipe == null) {
            log.warn("Không tìm thấy recipe cho {} tại {}", product.getCode(), reconDate);
            return CostResult.zero(product.getCode());
        }

        BigDecimal totalCost = BigDecimal.ZERO;

        // 2. Tính cost từ base recipe
        totalCost = totalCost.add(
            calculateRecipeCost(baseRecipe, reconDate, branch)
        );

        // 3. Nếu có SKU → load addon recipe
        if (skuCode != null) {
            var mappingOpt = productMappingRepository.findBySkuCode(skuCode);
            if (mappingOpt.isPresent() && mappingOpt.get().getRecipeAddon() != null) {
                Recipe addonRecipe = mappingOpt.get().getRecipeAddon();
                totalCost = totalCost.add(
                    calculateRecipeCost(addonRecipe, reconDate, branch)
                );
                log.debug("Cost {} (SKU: {}) = base + addon = {}",
                    product.getCode(), skuCode, totalCost);
            }
        }

        return new CostResult(product.getCode(), totalCost, true);
    }

    /**
     * Tính cost từ 1 recipe (base hoặc addon).
     * Mỗi ingredient dùng giá CAO NHẤT trong kho hiện tại.
     */
    private BigDecimal calculateRecipeCost(
            Recipe recipe, LocalDate reconDate, Branch branch) {

        BigDecimal total = BigDecimal.ZERO;

        for (RecipeLine line : recipe.getLines()) {
            BigDecimal lineCost;

            if (line.getSemiProduct() != null) {
                // Semi product: tính cost on-the-fly (semi_product_cost đã bị xóa V15)
                BigDecimal costPerKg = costCalculationService
                    .calculateCostPerKg(line.getSemiProduct(), branch);
                lineCost = line.getQuantityGram()
                    .divide(BigDecimal.valueOf(1000), 10, RoundingMode.HALF_UP)
                    .multiply(costPerKg);

            } else if (line.getIngredient() != null) {
                // Ingredient: lấy giá CAO NHẤT lô đang còn trong kho
                BigDecimal maxPrice = getMaxPriceInStock(
                    line.getIngredient().getId(), branch.getId()
                );

                // VNĐ/gram = price_per_kg / 1,000,000
                BigDecimal pricePerGram = maxPrice
                    .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);

                lineCost = line.getQuantityGram().multiply(pricePerGram);
            } else {
                lineCost = BigDecimal.ZERO;
            }

            total = total.add(lineCost);
        }

        return total.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Lấy giá CAO NHẤT của nguyên liệu trong các lô đang còn hàng.
     * Chỉ xét lô có qty_remaining > 0 — KHÔNG lấy từ lịch sử.
     */
    private BigDecimal getMaxPriceInStock(UUID ingredientId, UUID branchId) {
        return stockLotRepository
            .findMaxUnitPriceInStock(ingredientId, branchId)
            .orElseGet(() -> {
                log.warn("Không có lô nào trong kho cho ingredient: {}", ingredientId);
                return BigDecimal.ZERO;
            });
    }

    // ── Result record ─────────────────────────────────────────

    public record CostResult(
        String     productCode,
        BigDecimal costPerUnit,
        boolean    hasStock       // false = không có lô nào trong kho
    ) {
        static CostResult zero(String code) {
            return new CostResult(code, BigDecimal.ZERO, false);
        }
    }
}
