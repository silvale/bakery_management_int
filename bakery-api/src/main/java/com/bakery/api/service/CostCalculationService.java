package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Tính giá thành bán thành phẩm on-the-fly từ giá nguyên liệu thực tế.
 * (semi_product_cost table đã bị xóa ở V15 — cost tính trực tiếp, không cache)
 *
 * <h3>Nguyên tắc Max Price:</h3>
 * <ul>
 *   <li>Lấy giá CAO NHẤT trong các lô nguyên liệu đang còn trong kho của chi nhánh chính.</li>
 *   <li>Nếu không có lô trong kho → fallback về ingredient_price (phiên bản mới nhất).</li>
 * </ul>
 *
 * <h3>Công thức:</h3>
 * <pre>
 *   cost_per_kg = SUM(recipe_line_semi.qty_in_batch × max_price_per_kg) / semi_product.total_yield_kg
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostCalculationService {

    private final SemiProductRepository        semiProductRepository;
    private final RecipeLineSemiRepository     recipeLineSemiRepository;
    private final IngredientStockLotRepository stockLotRepository;
    private final IngredientPriceRepository    ingredientPriceRepository;
    private final BranchRepository             branchRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Recalculate cost của TẤT CẢ semi-product đang active.
     * Dùng khi khởi động hoặc khi giá nguyên liệu thay đổi hàng loạt.
     *
     * @param mainBranchCode code của chi nhánh chính (dùng để lấy giá kho)
     */
    @Transactional
    public void recalculateAll(String mainBranchCode) {
        Branch branch = branchRepository.findByCode(mainBranchCode)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + mainBranchCode));

        List<SemiProduct> semiProducts = semiProductRepository.findAllByIsActiveTrue();
        log.info("Recalculating cost for {} semi-products (branch={})", semiProducts.size(), mainBranchCode);

        int success = 0, skipped = 0;
        for (SemiProduct sp : semiProducts) {
            try {
                recalculateSemiProduct(sp, branch);
                success++;
            } catch (Exception e) {
                log.warn("Skipping semi_product {} ({}): {}", sp.getCode(), sp.getName(), e.getMessage());
                skipped++;
            }
        }
        log.info("Done. success={}, skipped={}", success, skipped);
    }

    /**
     * Recalculate cost của 1 semi-product cụ thể (theo code).
     * Gọi sau khi nhập lô nguyên liệu mới ảnh hưởng đến semi-product này.
     */
    @Transactional
    public void recalculateByCode(String semiProductCode, String mainBranchCode) {
        Branch branch = branchRepository.findByCode(mainBranchCode)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + mainBranchCode));
        SemiProduct sp = semiProductRepository.findByCode(semiProductCode)
            .orElseThrow(() -> new IllegalArgumentException("SemiProduct not found: " + semiProductCode));
        recalculateSemiProduct(sp, branch);
    }

    /**
     * Trigger recalculate tất cả semi-product dùng nguyên liệu vừa nhập kho.
     * Gọi từ PurchaseOrderService sau khi save lô mới.
     */
    @Transactional
    public void onIngredientStockReceived(UUID ingredientId, String mainBranchCode) {
        Branch branch = branchRepository.findByCode(mainBranchCode)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + mainBranchCode));

        List<RecipeLineSemi> affected = recipeLineSemiRepository.findAllByIngredientId(ingredientId);
        if (affected.isEmpty()) {
            log.debug("Ingredient {} không thuộc semi-product nào — bỏ qua recalculate.", ingredientId);
            return;
        }

        // Collect unique semi-products
        affected.stream()
            .map(RecipeLineSemi::getSemiProduct)
            .distinct()
            .forEach(sp -> {
                try {
                    recalculateSemiProduct(sp, branch);
                } catch (Exception e) {
                    log.warn("Lỗi recalculate semi_product {} khi nhập kho nguyên liệu {}: {}",
                        sp.getCode(), ingredientId, e.getMessage());
                }
            });
    }

    // ── Core calculation ──────────────────────────────────────────────────────

    /**
     * Tính cost_per_kg on-the-fly cho 1 semi-product.
     * Không cache — gọi mỗi lần cần dùng.
     *
     * @return cost_per_kg (VND/kg), ZERO nếu không có công thức hoặc yield không hợp lệ
     */
    public BigDecimal calculateCostPerKg(SemiProduct sp, Branch branch) {
        List<RecipeLineSemi> lines = recipeLineSemiRepository.findAllBySemiProductId(sp.getId());
        if (lines.isEmpty()) {
            log.debug("Semi-product {} không có công thức nguyên liệu.", sp.getCode());
            return BigDecimal.ZERO;
        }

        BigDecimal batchCost = BigDecimal.ZERO;
        for (RecipeLineSemi line : lines) {
            BigDecimal pricePerKg = resolveMaxPrice(line.getIngredient(), branch);
            batchCost = batchCost.add(pricePerKg.multiply(line.getQtyInBatch()));
        }

        BigDecimal yieldKg = sp.getTotalYieldKg();
        if (yieldKg == null || yieldKg.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Semi-product {} có total_yield_kg không hợp lệ: {}", sp.getCode(), yieldKg);
            return BigDecimal.ZERO;
        }

        return batchCost.divide(yieldKg, 4, RoundingMode.HALF_UP);
    }

    private void recalculateSemiProduct(SemiProduct sp, Branch branch) {
        BigDecimal costPerKg = calculateCostPerKg(sp, branch);
        if (costPerKg.compareTo(BigDecimal.ZERO) > 0) {
            log.info("SemiProduct [{}] {} → cost_per_kg={} VND/kg (on-the-fly, không cache)",
                sp.getCode(), sp.getName(), costPerKg);
        }
    }

    /**
     * Lấy giá CAO NHẤT của nguyên liệu trong kho.
     * Fallback: giá mới nhất trong ingredient_price nếu không có lô nào.
     */
    private BigDecimal resolveMaxPrice(Ingredient ingredient, Branch branch) {
        // Ưu tiên: giá max trong lô đang có trong kho
        return stockLotRepository
            .findMaxUnitPriceInStock(ingredient.getId(), branch.getId())
            .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
            .orElseGet(() -> {
                // Fallback: ingredient_price table (giá nhập tay)
                return ingredientPriceRepository
                    .findLatestPrice(ingredient.getId())
                    .map(IngredientPrice::getPricePerKg)
                    .orElseGet(() -> {
                        log.warn("Không tìm thấy giá cho nguyên liệu {} ({}). Dùng 0.",
                            ingredient.getCode(), ingredient.getName());
                        return BigDecimal.ZERO;
                    });
            });
    }
}
