package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.repository.InventoryRequestLineRepository;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.api.production.repository.ProductionPlanLineRepository;
import com.bakery.api.production.repository.ProductionPlanRepository;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeLineRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.WarehouseType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra nguyên liệu cho kế hoạch sản xuất và tạo phiếu xuất kho MAIN→KITCHEN.
 *
 * <p>Flow:
 * 1. {@link #checkIngredients(UUID)} — expand BOM 2 tầng (PRODUCT→SEMI→INGREDIENT),
 *    so sánh với tồn kho MAIN warehouse, trả về danh sách đủ/thiếu.
 * 2. {@link #generateTransferRequest(UUID)} — nếu tất cả đủ (hoặc bỏ qua thiếu),
 *    tạo InventoryRequest TRANSFER MAIN→KITCHEN ở trạng thái PENDING_APPROVAL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionIngredientService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KITCHEN_CODE = "KITCHEN";

    private final ProductionPlanRepository planRepository;
    private final ProductionPlanLineRepository planLineRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final StockLotRepository stockLotRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRequestRepository inventoryRequestRepository;
    private final InventoryRequestLineRepository inventoryRequestLineRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final BakeryActorResolver actorResolver;

    // ── Public ───────────────────────────────────────────────────

    /**
     * Kiểm tra nguyên liệu cho kế hoạch sản xuất.
     *
     * @return Map với 2 keys:
     *   "sufficient" → List của items đủ hàng
     *   "shortage"   → List của items thiếu hàng (cần nhập thêm)
     *   Mỗi item chứa: itemId, itemCode, itemName, unit, needed, available, shortage
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkIngredients(UUID planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", planId));

        // Expand BOM → tổng NL cần theo từng item
        Map<UUID, IngredientNeed> needed = expandBom(plan);

        // Tải tồn kho MAIN warehouse một lần
        Warehouse mainWarehouse = findMainWarehouse();
        Map<UUID, BigDecimal> stockByItem = currentMainStock(mainWarehouse);

        List<Map<String, Object>> sufficient = new ArrayList<>();
        List<Map<String, Object>> shortage = new ArrayList<>();

        for (IngredientNeed need : needed.values()) {
            BigDecimal available = stockByItem.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal gap = available.subtract(need.totalQty);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId",    need.item.getId());
            row.put("itemCode",  need.item.getCode());
            row.put("itemName",  need.item.getName());
            row.put("unit",      need.item.getUnit());
            row.put("needed",    need.totalQty);
            row.put("available", available);
            row.put("shortage",  gap.negate().max(BigDecimal.ZERO)); // 0 nếu đủ

            if (gap.compareTo(BigDecimal.ZERO) >= 0) {
                sufficient.add(row);
            } else {
                shortage.add(row);
            }
        }

        // Sort: shortage theo mức thiếu giảm dần, sufficient theo tên
        shortage.sort((a, b) -> ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage")));
        sufficient.sort((a, b) -> String.valueOf(a.get("itemName")).compareTo(String.valueOf(b.get("itemName"))));

        return Map.of(
                "planId",      planId,
                "planDate",    plan.getPlanDate(),
                "sufficient",  sufficient,
                "shortage",    shortage,
                "allSufficient", shortage.isEmpty()
        );
    }

    /**
     * Tạo phiếu nhập kho PURCHASE vào MAIN cho các nguyên liệu đang thiếu.
     * Chỉ bao gồm shortage items — đủ rồi thì không cần nhập thêm.
     *
     * @return InventoryRequest PURCHASE đã tạo (PENDING_APPROVAL)
     * @throws IllegalStateException nếu không có nguyên liệu thiếu
     */
    @Transactional
    public InventoryRequest generatePurchaseRequest(UUID planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", planId));

        Map<UUID, IngredientNeed> needed = expandBom(plan);
        Warehouse mainWarehouse = findMainWarehouse();
        Map<UUID, BigDecimal> stockByItem = currentMainStock(mainWarehouse);

        // Lọc chỉ lấy những NL thiếu
        List<IngredientNeed> shortageItems = needed.values().stream()
                .filter(need -> {
                    BigDecimal available = stockByItem.getOrDefault(need.item.getId(), BigDecimal.ZERO);
                    return available.subtract(need.totalQty).compareTo(BigDecimal.ZERO) < 0;
                })
                .toList();

        if (shortageItems.isEmpty()) {
            throw new IllegalStateException("Không có nguyên liệu thiếu — không cần tạo phiếu nhập.");
        }

        // Tạo phiếu PURCHASE vào MAIN
        InventoryRequest request = new InventoryRequest();
        request.setRequestType(InventoryRequestType.PURCHASE);
        request.setRequestDate(LocalDate.now());
        request.setExpectedDeliveryDate(plan.getPlanDate());
        request.setTargetWarehouse(mainWarehouse);
        request.setNote("Nhập NL thiếu cho kế hoạch SX ngày " + plan.getPlanDate()
                + " (plan #" + planId.toString().substring(0, 8) + ")");
        request.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);

        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = "PO-" + dateStr + "-";
        long count = inventoryRequestRepository.countByCodeStartingWith(codePrefix);
        request.setCode(codePrefix + String.format("%03d", count + 1));

        InventoryRequest saved = inventoryRequestRepository.save(request);

        int order = 1;
        for (IngredientNeed need : shortageItems) {
            BigDecimal available = stockByItem.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal shortageQty = need.totalQty.subtract(available); // lượng cần nhập thêm

            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(saved);
            line.setItem(need.item);
            line.setQuantity(shortageQty);
            line.setUnit(need.item.getUnit() != null ? need.item.getUnit() : "kg");
            line.setSortOrder(order++);
            line.setNote("Cần " + need.totalQty + ", tồn " + available + " → nhập thêm " + shortageQty);
            line.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            inventoryRequestLineRepository.save(line);
        }

        log.info("Đã tạo phiếu nhập kho {} ({} NL thiếu) cho kế hoạch SX ngày {}",
                saved.getCode(), shortageItems.size(), plan.getPlanDate());
        return saved;
    }

    /**
     * Tạo phiếu xuất kho TRANSFER MAIN→KITCHEN cho kế hoạch sản xuất.
     * Bao gồm tất cả nguyên liệu cần (kể cả thiếu — manager duyệt riêng).
     *
     * @return InventoryRequest đã tạo (ở trạng thái PENDING_APPROVAL)
     */
    @Transactional
    public InventoryRequest generateTransferRequest(UUID planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", planId));

        // Kiểm tra đã có phiếu TR cho plan này chưa (theo code prefix)
        String prefix = "TR-" + plan.getPlanDate().format(DATE_FMT);
        // Không block nếu đã có — cho phép tạo nhiều phiếu (manager tự manage)

        Map<UUID, IngredientNeed> needed = expandBom(plan);
        if (needed.isEmpty()) {
            throw new IllegalStateException("Kế hoạch SX không có nguyên liệu cần xuất (chưa có công thức?).");
        }

        Warehouse mainWarehouse = findMainWarehouse();
        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN."));

        // Chỉ xuất những NL có đủ tồn MAIN — thiếu thì bỏ qua (cần mua trước)
        Map<UUID, BigDecimal> mainStock = currentMainStock(mainWarehouse);
        List<IngredientNeed> transferable = needed.values().stream()
                .filter(need -> mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO)
                        .compareTo(need.totalQty) >= 0)
                .toList();

        List<String> skipped = needed.values().stream()
                .filter(need -> mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO)
                        .compareTo(need.totalQty) < 0)
                .map(need -> need.item.getName())
                .toList();

        if (!skipped.isEmpty()) {
            log.warn("generateTransferRequest: bỏ qua {} NL thiếu tồn MAIN: {}", skipped.size(), skipped);
        }

        if (transferable.isEmpty()) {
            throw new IllegalStateException(
                    "Không có NL nào đủ tồn MAIN để xuất. Cần nhập kho trước: " + skipped);
        }

        // Tạo phiếu TRANSFER
        InventoryRequest request = new InventoryRequest();
        request.setRequestType(InventoryRequestType.TRANSFER);
        request.setRequestDate(LocalDate.now());
        request.setExpectedDeliveryDate(plan.getPlanDate());
        request.setSourceWarehouse(mainWarehouse);
        request.setTargetWarehouse(kitchen);
        request.setNote("Xuất NL cho kế hoạch SX ngày " + plan.getPlanDate()
                + " (plan #" + planId.toString().substring(0, 8) + ")"
                + (skipped.isEmpty() ? "" : " [bỏ qua " + skipped.size() + " NL thiếu]"));
        request.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);

        // Auto-generate code: TR-YYYYMMDD-NNN
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = "TR-" + dateStr + "-";
        long count = inventoryRequestRepository.countByCodeStartingWith(codePrefix);
        request.setCode(codePrefix + String.format("%03d", count + 1));

        InventoryRequest saved = inventoryRequestRepository.save(request);

        // Thêm lines chỉ cho NL đủ tồn MAIN
        int order = 1;
        for (IngredientNeed need : transferable) {
            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(saved);
            line.setItem(need.item);
            line.setQuantity(need.totalQty);
            line.setUnit(need.item.getUnit() != null ? need.item.getUnit() : "kg");
            line.setSortOrder(order++);
            line.setNote("Từ kế hoạch SX " + plan.getPlanDate());
            line.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            inventoryRequestLineRepository.save(line);
        }

        log.info("Đã tạo phiếu xuất kho {} ({} NL) cho kế hoạch SX ngày {}",
                saved.getCode(), needed.size(), plan.getPlanDate());
        return saved;
    }

    // ── Private helpers ──────────────────────────────────────────

    /**
     * Expand BOM 2 tầng: PRODUCT recipe → SEMI_PRODUCT recipe → INGREDIENT.
     * Tổng hợp theo ingredient_id, nhân với adjustedQty (hoặc suggestedQty) của plan line.
     *
     * <p><b>FREE_GROUP fallback</b>: Nếu plan line thuộc FREE_GROUP có {@code suggestedQty = null}
     * (admin chưa phân bổ từng flavor), và group có {@code baseRecipe}, hệ thống tự động dùng
     * {@code baseRecipe × group target qty} để tính NL. Mỗi group chỉ expand 1 lần.
     */
    private Map<UUID, IngredientNeed> expandBom(ProductionPlan plan) {
        List<ProductionPlanLine> lines = planLineRepository.findByPlanIdOrderBySortOrderAsc(plan.getId());
        Map<UUID, IngredientNeed> result = new LinkedHashMap<>();
        // Track groups đã expand base_recipe (tránh double-count)
        java.util.Set<UUID> expandedGroupIds = new java.util.HashSet<>();

        for (ProductionPlanLine planLine : lines) {
            Item product = planLine.getItem();
            if (product == null) continue;

            // qty để SX (manager có thể đã điều chỉnh)
            Integer rawQty = planLine.getAdjustedQty() != null
                    ? planLine.getAdjustedQty()
                    : planLine.getSuggestedQty();

            // ── FREE_GROUP fallback: qty null → dùng baseRecipe của group ────────
            com.bakery.api.production.entity.ProductionGroup group = planLine.getGroup();
            if ((rawQty == null || rawQty <= 0) && group != null && group.getBaseRecipe() != null) {
                if (!expandedGroupIds.contains(group.getId())) {
                    expandedGroupIds.add(group.getId());
                    BigDecimal groupQty = resolveGroupTargetQty(group, plan.getPlanDate());
                    if (groupQty.compareTo(BigDecimal.ZERO) > 0) {
                        log.debug("expandBom: nhóm {} (FREE_GROUP) dùng base_recipe × {} thay vì per-item",
                                group.getCode(), groupQty);
                        expandRecipe(group.getBaseRecipe(), groupQty, result);
                    }
                }
                continue;
            }

            if (rawQty == null || rawQty <= 0) continue;
            BigDecimal qty = BigDecimal.valueOf(rawQty);

            // Lấy active recipe của PRODUCT (per-item — khi manager đã phân bổ qty)
            recipeRepository.findByProductIdAndActiveTrue(product.getId())
                    .ifPresent(recipe -> expandRecipe(recipe, qty, result));
        }
        return result;
    }

    /**
     * Tính target qty của group theo ngày trong tuần.
     * Thứ 7/CN = WEEKEND, còn lại = WEEKDAY.
     */
    private BigDecimal resolveGroupTargetQty(
            com.bakery.api.production.entity.ProductionGroup group, java.time.LocalDate date) {
        java.time.DayOfWeek dow = date.getDayOfWeek();
        boolean isWeekend = dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
        int target = isWeekend
                ? (group.getTargetWeekend() != null ? group.getTargetWeekend() : 0)
                : (group.getTargetWeekday() != null ? group.getTargetWeekday() : 0);
        return BigDecimal.valueOf(target);
    }

    /**
     * Expand 1 recipe: với mỗi recipe_line:
     *   - INGREDIENT → cộng vào result
     *   - SEMI_PRODUCT → đệ quy expand recipe của semi_product
     */
    private void expandRecipe(Recipe recipe, BigDecimal multiplier, Map<UUID, IngredientNeed> result) {
        List<RecipeLine> lines = recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
        for (RecipeLine rl : lines) {
            Item item = rl.getItem();
            if (item == null) continue;

            // Quy đổi đơn vị: line.unit (đvt công thức, e.g. G) → item.unit (đvt kho, e.g. KG)
            BigDecimal convFactor = resolveConversionFactor(rl.getUnit(), item.getUnit());
            BigDecimal needed = rl.getQuantity().multiply(multiplier).multiply(convFactor);

            if (item instanceof SemiProduct) {
                // Tầng 2: expand recipe của semi_product (multiplier đã quy đổi sang đvt của semi)
                recipeRepository.findBySemiProductIdAndActiveTrue(item.getId())
                        .ifPresent(subRecipe -> expandRecipe(subRecipe, needed, result));
            } else {
                // INGREDIENT — cộng vào result (qty đã tính theo item.unit)
                result.merge(item.getId(),
                        new IngredientNeed(item, needed),
                        (existing, newNeed) -> {
                            existing.totalQty = existing.totalQty.add(newNeed.totalQty);
                            return existing;
                        });
            }
        }
    }

    /**
     * Tra hệ số quy đổi từ {@code lineUnit} (đơn vị công thức) sang {@code itemUnit} (đơn vị kho).
     * Ví dụ: G → KG = 0.001, ML → L = 0.001.
     * Cùng đơn vị hoặc không tìm thấy → 1 (log warning).
     */
    private BigDecimal resolveConversionFactor(String lineUnit, String itemUnit) {
        if (lineUnit == null || itemUnit == null) return BigDecimal.ONE;
        if (lineUnit.equalsIgnoreCase(itemUnit)) return BigDecimal.ONE;
        return unitConversionRepository.findConversion(lineUnit, itemUnit)
                .map(uc -> uc.getFactor())
                .orElseGet(() -> {
                    log.warn("Không tìm thấy unit conversion: {} → {} — dùng factor=1, kết quả có thể sai!",
                            lineUnit, itemUnit);
                    return BigDecimal.ONE;
                });
    }

    private Map<UUID, BigDecimal> currentMainStock(Warehouse mainWarehouse) {
        // Load tất cả lots trong kho MAIN và group theo item_id
        return stockLotRepository.findByWarehouseCode(mainWarehouse.getCode()).stream()
                .filter(lot -> lot.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        lot -> lot.getItem().getId(),
                        Collectors.reducing(BigDecimal.ZERO,
                                lot -> lot.getQtyRemaining(),
                                BigDecimal::add)
                ));
    }

    private Warehouse findMainWarehouse() {
        return warehouseRepository.findByWarehouseType(WarehouseType.MAIN).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho MAIN."));
    }

    /** Mutable holder cho tổng nguyên liệu cần. */
    private static class IngredientNeed {
        final Item item;
        BigDecimal totalQty;

        IngredientNeed(Item item, BigDecimal qty) {
            this.item = item;
            this.totalQty = qty;
        }
    }
}
