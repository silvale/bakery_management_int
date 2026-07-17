/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.UnitConversion;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.api.pricing.repository.IngredientPriceRepository;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tính giá cost per unit cho Product / SemiProduct dựa trên active recipe.
 *
 * <p>Nguồn giá (ưu tiên theo thứ tự):
 * <ol>
 *   <li>Catalog price — bảng {@code ingredient_price}, lấy giá mới nhất theo {@code effective_date}</li>
 *   <li>Giá lô thực tế — weighted average của {@code stock_lot.unit_cost} tại kho KITCHEN</li>
 *   <li>MISSING — đánh dấu {@code complete=false} nếu không tìm thấy nguồn nào</li>
 * </ol>
 *
 * <p>Hỗ trợ BOM 2 tầng: nếu recipe line là SemiProduct, hệ thống đệ quy tính cost của SemiProduct đó
 * qua active recipe của nó. Vòng lặp circular được chặn bằng {@code visitedIds}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeCostService {

    private static final String KITCHEN_CODE = "KITCHEN";
    private static final int COST_SCALE = 4;

    private final RecipeRepository recipeRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final StockLotRepository stockLotRepository;
    private final ItemLookupRepository itemRepository;
    private final UnitConversionRepository unitConversionRepository;

    // ── Public API ────────────────────────────────────────────────

    /**
     * Tính cost per unit cho 1 item (Product hoặc SemiProduct).
     *
     * @param itemId ID của Product hoặc SemiProduct
     * @return {@link CostResult} với breakdown chi tiết từng nguyên liệu
     * @throws IllegalStateException nếu item không có active recipe
     */
    public CostResult calculate(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        return calculateInternal(item, BigDecimal.ONE, new HashSet<>());
    }

    /**
     * Tính cost cho nhiều items cùng lúc — tiện dùng khi render danh sách sản phẩm.
     */
    public List<CostResult> calculateBatch(List<UUID> itemIds) {
        return itemIds.stream().map(this::calculate).toList();
    }

    // ── Core logic ────────────────────────────────────────────────

    /**
     * @param item        item cần tính cost
     * @param multiplier  hệ số nhân (= quantity của line cha khi đệ quy SemiProduct)
     * @param visitedIds  tập ID đã đi qua — chặn vòng lặp circular trong BOM
     */
    private CostResult calculateInternal(Item item, BigDecimal multiplier, Set<UUID> visitedIds) {
        if (!visitedIds.add(item.getId())) {
            throw new IllegalStateException(
                    "Phát hiện vòng lặp circular trong BOM: item " + item.getCode());
        }

        Recipe recipe = findActiveRecipe(item);
        List<LineCost> breakdown = new ArrayList<>();
        boolean complete = true;

        for (RecipeLine line : recipe.getLines()) {
            Item lineItem = line.getItem();
            BigDecimal qty = line.getQuantity().multiply(multiplier);

            if (lineItem instanceof SemiProduct) {
                // BOM 2 tầng: đệ quy tính cost của SemiProduct này
                CostResult subResult = calculateInternal(lineItem, qty, new HashSet<>(visitedIds));
                breakdown.add(new LineCost(
                        lineItem.getCode(),
                        lineItem.getName(),
                        "SEMI_PRODUCT",
                        qty,
                        line.getUnit(),
                        subResult.totalCostPerUnit(),
                        subResult.totalCostPerUnit().multiply(qty).setScale(COST_SCALE, RoundingMode.HALF_UP),
                        "RECIPE_CALCULATED",
                        subResult.breakdown()
                ));
                if (!subResult.complete()) complete = false;
            } else {
                // Ingredient (hoặc Product dùng trong recipe — hiếm nhưng không block)
                PriceResolution resolved = resolveIngredientPrice(lineItem.getId());

                // Quy đổi đơn vị: line.unit (đvt công thức) → item.unit (đvt giá nhập)
                String lineUnit  = line.getUnit();
                String itemUnit  = lineItem.getUnit();
                ConversionResult conv = resolveConversionFactor(lineUnit, itemUnit);

                // cost = qty(line_unit) × factor(line→item) × unit_cost(per item_unit)
                BigDecimal effectiveUnitPrice = resolved.unitPrice().multiply(conv.factor());
                BigDecimal lineCost = effectiveUnitPrice
                        .multiply(qty)
                        .setScale(COST_SCALE, RoundingMode.HALF_UP);

                String source = resolved.source().equals("MISSING")
                        ? "MISSING"
                        : conv.source().equals("UNIT_MISMATCH")
                                ? "UNIT_MISMATCH"
                                : resolved.source();

                breakdown.add(new LineCost(
                        lineItem.getCode(),
                        lineItem.getName(),
                        lineItem instanceof Ingredient ? "INGREDIENT" : "OTHER",
                        qty,
                        lineUnit,
                        effectiveUnitPrice,
                        lineCost,
                        source,
                        List.of()
                ));
                if (source.equals("MISSING") || source.equals("UNIT_MISMATCH")) complete = false;
            }
        }

        BigDecimal totalCost = breakdown.stream()
                .map(LineCost::lineCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(COST_SCALE, RoundingMode.HALF_UP);

        visitedIds.remove(item.getId());

        return new CostResult(
                item.getId(),
                item.getCode(),
                item.getName(),
                totalCost,
                breakdown,
                complete
        );
    }

    // ── Price resolution ─────────────────────────────────────────

    /**
     * Ưu tiên 1: catalog price mới nhất.
     * Ưu tiên 2: weighted average của stock lot tại KITCHEN.
     * Fallback: ZERO + source="MISSING".
     */
    private PriceResolution resolveIngredientPrice(UUID itemId) {
        // 1. Catalog price
        List<IngredientPrice> prices = ingredientPriceRepository
                .findByItemIdOrderByEffectiveDateDesc(itemId);
        if (!prices.isEmpty()) {
            return new PriceResolution(prices.get(0).getPrice(), "CATALOG");
        }

        // 2. Weighted average stock lot cost (KITCHEN only)
        List<StockLot> lots = stockLotRepository
                .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                        itemId, BigDecimal.ZERO)
                .stream()
                .filter(l -> l.getWarehouse() != null
                        && KITCHEN_CODE.equals(l.getWarehouse().getCode()))
                .toList();

        if (!lots.isEmpty()) {
            BigDecimal totalValue = lots.stream()
                    .map(l -> l.getUnitCost().multiply(l.getQtyRemaining()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalQty = lots.stream()
                    .map(StockLot::getQtyRemaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal weightedAvg = totalValue.divide(totalQty, COST_SCALE, RoundingMode.HALF_UP);
            return new PriceResolution(weightedAvg, "STOCK_LOT_AVG");
        }

        // 3. Không tìm thấy giá
        return new PriceResolution(BigDecimal.ZERO, "MISSING");
    }

    // ── Active recipe lookup ──────────────────────────────────────

    private Recipe findActiveRecipe(Item item) {
        if (item instanceof SemiProduct) {
            return recipeRepository.findBySemiProductIdAndActiveTrue(item.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "SemiProduct '" + item.getCode() + "' chưa có active recipe."));
        }
        return recipeRepository.findByProductIdAndActiveTrue(item.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Product '" + item.getCode() + "' chưa có active recipe."));
    }

    // ── Result types ─────────────────────────────────────────────

    /**
     * Kết quả tính cost cho 1 item.
     *
     * @param itemId          ID item
     * @param itemCode        Mã item
     * @param itemName        Tên item
     * @param totalCostPerUnit Tổng cost để sản xuất 1 đơn vị
     * @param breakdown       Chi tiết từng line nguyên liệu
     * @param complete        false nếu có ít nhất 1 NL chưa tìm được giá (MISSING)
     */
    public record CostResult(
            UUID itemId,
            String itemCode,
            String itemName,
            BigDecimal totalCostPerUnit,
            List<LineCost> breakdown,
            boolean complete) {}

    /**
     * Chi tiết cost của 1 dòng nguyên liệu / bán thành phẩm trong công thức.
     *
     * @param itemCode     Mã nguyên liệu / SemiProduct
     * @param itemName     Tên
     * @param itemType     INGREDIENT | SEMI_PRODUCT | OTHER
     * @param quantity     Số lượng dùng (đã nhân hệ số nếu từ SemiProduct đệ quy)
     * @param unit         Đơn vị
     * @param unitPrice    Giá 1 đơn vị nguyên liệu
     * @param lineCost     quantity × unitPrice
     * @param priceSource  CATALOG | STOCK_LOT_AVG | RECIPE_CALCULATED | MISSING
     * @param subBreakdown breakdown đệ quy (chỉ có dữ liệu khi itemType = SEMI_PRODUCT)
     */
    public record LineCost(
            String itemCode,
            String itemName,
            String itemType,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal lineCost,
            String priceSource,
            List<LineCost> subBreakdown) {}

    /** Nội bộ: kết quả tra giá 1 nguyên liệu. */
    private record PriceResolution(BigDecimal unitPrice, String source) {}

    /** Nội bộ: kết quả tra bảng unit_conversion. */
    private record ConversionResult(BigDecimal factor, String source) {}

    // ── Unit conversion ───────────────────────────────────────────

    /**
     * Tra hệ số quy đổi từ {@code lineUnit} (đơn vị công thức) sang {@code itemUnit} (đơn vị giá).
     *
     * <ul>
     *   <li>Cùng đơn vị → factor = 1, source = "SAME_UNIT"</li>
     *   <li>Tìm thấy trong bảng → factor, source = "CONVERTED"</li>
     *   <li>Không tìm thấy → factor = 1, source = "UNIT_MISMATCH" (đánh dấu cost không chính xác)</li>
     * </ul>
     */
    private ConversionResult resolveConversionFactor(String lineUnit, String itemUnit) {
        if (lineUnit == null || itemUnit == null) {
            return new ConversionResult(BigDecimal.ONE, "UNIT_MISMATCH");
        }
        if (lineUnit.equalsIgnoreCase(itemUnit)) {
            return new ConversionResult(BigDecimal.ONE, "SAME_UNIT");
        }
        return unitConversionRepository.findConversion(lineUnit, itemUnit)
                .map(uc -> new ConversionResult(uc.getFactor(), "CONVERTED"))
                .orElse(new ConversionResult(BigDecimal.ONE, "UNIT_MISMATCH"));
    }
}
