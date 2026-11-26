package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeLineRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.entity.ProductionLineStatus;
import com.bakery.framework.entity.ProductionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 24: nhu cầu NL/BTP của phiếu SX theo công thức, đã quy đổi về đơn vị kho. Một nơi cho hoàn thành (§5.5),
 * phiếu lấy tự tạo (§5.1) và danh sách NL của phiếu (§8). Chỉ tầng công thức trực tiếp, không bung BTP.
 */
@Component
@RequiredArgsConstructor
public class ProductionNeeds {

    private final RecipeRepository recipeRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final UnitConversionRepository unitConversionRepository;

    public record Need(Item item, BigDecimal qty) {}

    /** Công thức của dòng: công thức ghi trên dòng, không có thì công thức đang dùng (BTP tìm theo {@code semi_product_id}). */
    public Recipe recipeOf(ProductionRequestLine line, ProductionType type) {
        if (line.getRecipe() != null) return line.getRecipe();
        if (line.getProduct() == null) return null;
        UUID id = line.getProduct().getId();
        return (type == ProductionType.SEMI ? recipeRepository.findBySemiProductIdAndActiveTrue(id)
                : recipeRepository.findByProductIdAndActiveTrue(id)).orElse(null);
    }

    /** NL của một dòng làm {@code qty}: công thức × qty × hệ số quy đổi sang đơn vị kho, gộp theo mặt hàng, 4 số lẻ. */
    public Map<UUID, Need> forLine(ProductionRequestLine line, ProductionType type, BigDecimal qty) {
        Map<UUID, Need> out = new LinkedHashMap<>();
        Recipe recipe = recipeOf(line, type);
        if (recipe == null || qty == null || qty.signum() <= 0) return out;
        for (RecipeLine rl : recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId())) {
            if (rl.getItem() == null) continue;
            BigDecimal need = rl.getQuantity().multiply(qty).multiply(factor(rl.getUnit(), rl.getItem().getUnit()))
                    .setScale(4, RoundingMode.HALF_UP);
            out.merge(rl.getItem().getId(), new Need(rl.getItem(), need), ProductionNeeds::add);
        }
        return out;
    }

    /** NL của cả phiếu = công thức × số kế hoạch từng dòng; {@code openOnly} bỏ dòng đã hoàn thành. Dòng số mở (0) bỏ qua. */
    public Map<UUID, Need> forRequest(ProductionRequest pr, boolean openOnly) {
        Map<UUID, Need> out = new LinkedHashMap<>();
        for (ProductionRequestLine l : pr.getLines()) {
            if (openOnly && l.getLineStatus() == ProductionLineStatus.COMPLETED) continue;
            forLine(l, pr.getProductionType(), l.getPlannedQty()).forEach((id, n) -> out.merge(id, n, ProductionNeeds::add));
        }
        return out;
    }

    private static Need add(Need a, Need b) { return new Need(a.item(), a.qty().add(b.qty())); }

    private BigDecimal factor(String from, String to) {
        if (from == null || to == null || from.equalsIgnoreCase(to)) return BigDecimal.ONE;
        return unitConversionRepository.findConversion(from, to).map(uc -> uc.getFactor()).orElse(BigDecimal.ONE);
    }
}
