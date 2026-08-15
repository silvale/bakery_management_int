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
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanGroup;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.production.repository.ProductionPlanGroupRepository;
import com.bakery.api.production.repository.ProductionPlanLineRepository;
import com.bakery.api.production.repository.ProductionPlanRepository;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.framework.entity.ProductionType;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeLineRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.api.pricing.repository.IngredientPriceRepository;
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
    private final ProductionPlanGroupRepository planGroupRepository;
    private final ProductionRequestRepository prRepository;
    private final ItemLookupRepository itemRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final StockLotRepository stockLotRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRequestRepository inventoryRequestRepository;
    private final InventoryRequestLineRepository inventoryRequestLineRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final BakeryActorResolver actorResolver;

    // ── Public ───────────────────────────────────────────────────

    /**
     * Kiểm tra nguyên liệu cho kế hoạch sản xuất.
     *
     * @return Map với các keys:
     *   "sufficient"   → List items đủ hàng (raw ingredients)
     *   "shortage"     → List items thiếu hàng (raw ingredients)
     *   "semiNeeds"    → List BTP cần sản xuất (so với tồn KITCHEN)
     *   "allSufficient"→ true nếu không có raw ingredient nào thiếu
     *   Mỗi item chứa: itemId, itemCode, itemName, unit, needed, available, shortage
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkIngredients(UUID planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", planId));

        // Expand BOM → tổng raw ingredient cần theo từng item
        Map<UUID, IngredientNeed> needed = expandBom(plan);

        // Tải tồn kho MAIN + KITCHEN — available = tổng cả 2 kho
        Warehouse mainWarehouse = findMainWarehouse();
        Map<UUID, BigDecimal> mainStock = currentMainStock(mainWarehouse);
        Map<UUID, BigDecimal> kitchenStock = currentKitchenStock();

        List<Map<String, Object>> sufficient = new ArrayList<>();
        List<Map<String, Object>> shortage = new ArrayList<>();

        for (IngredientNeed need : needed.values()) {
            BigDecimal inMain    = mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal inKitchen = kitchenStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal available = inMain.add(inKitchen);
            BigDecimal gap = available.subtract(need.totalQty);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId",      need.item.getId());
            row.put("itemCode",    need.item.getCode());
            row.put("itemName",    need.item.getName());
            row.put("unit",        need.item.getUnit());
            row.put("needed",      need.totalQty);
            row.put("inMain",      inMain);
            row.put("inKitchen",   inKitchen);
            row.put("available",   available);
            row.put("shortage",    gap.negate().max(BigDecimal.ZERO)); // 0 nếu đủ

            if (gap.compareTo(BigDecimal.ZERO) >= 0) {
                sufficient.add(row);
            } else {
                shortage.add(row);
            }
        }

        // Sort: shortage theo mức thiếu giảm dần, sufficient theo tên
        shortage.sort((a, b) -> ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage")));
        sufficient.sort((a, b) -> String.valueOf(a.get("itemName")).compareTo(String.valueOf(b.get("itemName"))));

        // ── BTP (SEMI_PRODUCT) needs — so với tồn KITCHEN ─────────────────
        List<Map<String, Object>> semiNeeds = buildSemiNeeds(plan, kitchenStock);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId",       planId);
        result.put("planDate",     plan.getPlanDate());
        result.put("sufficient",   sufficient);
        result.put("shortage",     shortage);
        result.put("allSufficient", shortage.isEmpty());
        result.put("semiNeeds",    semiNeeds);
        return result;
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
        Map<UUID, BigDecimal> mainStock = currentMainStock(mainWarehouse);
        Map<UUID, BigDecimal> kitchenStock = currentKitchenStock();

        // Lọc chỉ lấy những NL thiếu (so với tổng MAIN + KITCHEN)
        List<IngredientNeed> shortageItems = needed.values().stream()
                .filter(need -> {
                    BigDecimal available = mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO)
                            .add(kitchenStock.getOrDefault(need.item.getId(), BigDecimal.ZERO));
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

        // Pre-load giá mới nhất cho tất cả NL thiếu (1 query)
        List<UUID> itemIds = shortageItems.stream().map(n -> n.item.getId()).toList();
        Map<UUID, BigDecimal> latestPriceByItem = ingredientPriceRepository
                .findLatestByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        ip -> ip.getItem().getId(),
                        IngredientPrice::getPrice,
                        (a, b) -> a));

        int order = 1;
        for (IngredientNeed need : shortageItems) {
            BigDecimal inMain    = mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal inKitchen = kitchenStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal shortageQty = need.totalQty.subtract(inMain).subtract(inKitchen); // lượng cần nhập thêm

            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(saved);
            line.setItem(need.item);
            line.setQuantity(shortageQty);
            line.setUnit(need.item.getUnit() != null ? need.item.getUnit() : "kg");
            // Giá ưu tiên: ingredient_price history → fallback item.unitCost
            BigDecimal unitCost = latestPriceByItem.getOrDefault(need.item.getId(), need.item.getUnitCost());
            line.setUnitCost(unitCost);
            line.setSortOrder(order++);
            line.setNote("Cần " + need.totalQty + ", tồn MAIN " + inMain + " + BẾP " + inKitchen + " → nhập thêm " + shortageQty);
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

        Map<UUID, BigDecimal> mainStock    = currentMainStock(mainWarehouse);
        Map<UUID, BigDecimal> kitchenStock = currentKitchenStock();

        // Tính qty cần transfer = needed - kitchenStock (chỉ phần KITCHEN chưa có)
        // Capped bởi tồn MAIN. NL KITCHEN đã đủ → skip.
        List<IngredientNeed> transferable = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (IngredientNeed need : needed.values()) {
            BigDecimal inKitchen = kitchenStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal deficit   = need.totalQty.subtract(inKitchen);
            if (deficit.compareTo(BigDecimal.ZERO) <= 0) {
                // KITCHEN đã đủ, không cần chuyển
                continue;
            }
            BigDecimal inMain = mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            if (inMain.compareTo(BigDecimal.ZERO) <= 0) {
                skipped.add(need.item.getName() + " (MAIN hết tồn)");
                continue;
            }
            // Chỉ transfer tối đa tồn MAIN
            BigDecimal transferQty = deficit.min(inMain);
            transferable.add(new IngredientNeed(need.item, transferQty));
        }

        if (!skipped.isEmpty()) {
            log.warn("generateTransferRequest: bỏ qua {} NL thiếu tồn MAIN: {}", skipped.size(), skipped);
        }

        if (transferable.isEmpty()) {
            throw new IllegalStateException(
                    "Không có NL nào cần chuyển (KITCHEN đã đủ hoặc MAIN không có tồn)."
                    + (skipped.isEmpty() ? "" : " NL thiếu MAIN: " + skipped));
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
                + (skipped.isEmpty() ? "" : " [bỏ qua " + skipped.size() + " NL thiếu MAIN]"));
        request.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);

        // Auto-generate code: TR-YYYYMMDD-NNN
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = "TR-" + dateStr + "-";
        long count = inventoryRequestRepository.countByCodeStartingWith(codePrefix);
        request.setCode(codePrefix + String.format("%03d", count + 1));

        InventoryRequest saved = inventoryRequestRepository.save(request);

        // Lines: qty đã trừ phần KITCHEN có sẵn
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
     *
     * <p><b>BATCH_FORMULA</b>: NL = {@code baseRecipe × plannedQty} (plannedQty = số cối từ
     * {@link com.bakery.api.production.entity.ProductionPlanGroup}). Không expand per-item.
     *
     * <p><b>FREE_GROUP</b>: NL = {@code baseRecipe × plannedQty} (plannedQty = target đã resolve
     * weekday/weekend). Không expand per-item flavor.
     *
     * <p><b>Thường (SIMPLE)</b>: expand per-item recipe × adjustedQty (hoặc suggestedQty).
     *
     * <p>Mỗi group chỉ expand 1 lần (track bằng {@code expandedGroupIds}).
     */
    private Map<UUID, IngredientNeed> expandBom(ProductionPlan plan) {
        List<ProductionPlanLine> lines = planLineRepository.findByPlanIdOrderBySortOrderAsc(plan.getId());
        Map<UUID, IngredientNeed> result = new LinkedHashMap<>();
        java.util.Set<UUID> expandedGroupIds = new java.util.HashSet<>();

        // Load tất cả ProductionPlanGroup của plan này (1 query)
        Map<UUID, Integer> plannedQtyByGroupId = planGroupRepository.findByPlanId(plan.getId()).stream()
                .collect(Collectors.toMap(
                        ppg -> ppg.getGroup().getId(),
                        com.bakery.api.production.entity.ProductionPlanGroup::getPlannedQty));

        for (ProductionPlanLine planLine : lines) {
            Item product = planLine.getItem();
            if (product == null) continue;

            ProductionGroup group = planLine.getGroup();

            // ── GROUP-LEVEL EXPAND (FREE_GROUP hoặc BATCH_FORMULA với base_recipe) ──
            if (group != null && group.getBaseRecipe() != null
                    && ("FREE_GROUP".equals(group.getGroupType()) || "BATCH_FORMULA".equals(group.getGroupType()))) {
                if (!expandedGroupIds.contains(group.getId())) {
                    expandedGroupIds.add(group.getId());
                    // Ưu tiên plannedQty từ ProductionPlanGroup; fallback về target weekday/weekend cho FREE_GROUP
                    BigDecimal groupQty;
                    if (plannedQtyByGroupId.containsKey(group.getId())) {
                        groupQty = BigDecimal.valueOf(plannedQtyByGroupId.get(group.getId()));
                    } else {
                        groupQty = resolveGroupTargetQty(group, plan.getPlanDate());
                    }
                    if (groupQty.compareTo(BigDecimal.ZERO) > 0) {
                        // Nếu base_recipe thuộc về SemiProduct → ưu tiên active recipe của SP đó
                        // (tránh dùng phiên bản công thức cũ khi group.base_recipe_id chưa được cập nhật)
                        Recipe baseRecipe = group.getBaseRecipe();
                        if (baseRecipe.getSemiProduct() != null) {
                            baseRecipe = recipeRepository
                                    .findBySemiProductIdAndActiveTrue(baseRecipe.getSemiProduct().getId())
                                    .orElse(baseRecipe);
                        }
                        log.debug("expandBom: nhóm {} ({}) dùng base_recipe {} × {}",
                                group.getCode(), group.getGroupType(), baseRecipe.getProduct() != null ? baseRecipe.getProduct().getName() : "", groupQty);
                        expandRecipe(baseRecipe, groupQty, result);
                    }
                }
                // Với group-level expand: không expand per-item — skip luôn
                continue;
            }

            // ── PER-ITEM EXPAND (SIMPLE hoặc group chưa có base_recipe) ──
            Integer rawQty = planLine.getAdjustedQty() != null
                    ? planLine.getAdjustedQty()
                    : planLine.getSuggestedQty();
            if (rawQty == null || rawQty <= 0) continue;
            BigDecimal qty = BigDecimal.valueOf(rawQty);

            recipeRepository.findByProductIdAndActiveTrue(product.getId())
                    .ifPresent(recipe -> expandRecipe(recipe, qty, result));
        }
        return result;
    }

    /**
     * Fallback: tính target qty của group theo ngày trong tuần (khi chưa có ProductionPlanGroup).
     * Thứ 7/CN = WEEKEND, còn lại = WEEKDAY.
     */
    private BigDecimal resolveGroupTargetQty(ProductionGroup group, java.time.LocalDate date) {
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
            BigDecimal convFactor = resolveConversionFactor(rl.getUnit(), item);
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
     * Tra hệ số quy đổi từ {@code lineUnit} (đơn vị công thức) sang {@code item.unit} (đơn vị canonical).
     * Ví dụ: G → KG = 0.001, ML → L = 0.001.
     *
     * <p>Item.unit luôn là đơn vị canonical (KG, L, G...) kể từ V42.
     * Packaging được xử lý ở tầng nhập kho, không ảnh hưởng đến recipe / production planning.
     */
    private BigDecimal resolveConversionFactor(String lineUnit, Item item) {
        String itemUnit = item.getUnit();
        if (lineUnit == null || itemUnit == null) return BigDecimal.ONE;
        if (lineUnit.equalsIgnoreCase(itemUnit)) return BigDecimal.ONE;
        var direct = unitConversionRepository.findConversion(lineUnit, itemUnit);
        if (direct.isPresent()) return direct.get().getFactor();
        log.warn("Không tìm thấy unit conversion: {} → {} — dùng factor=1, kết quả có thể sai!",
                lineUnit, itemUnit);
        return BigDecimal.ONE;
    }

    private Map<UUID, BigDecimal> currentMainStock(Warehouse mainWarehouse) {
        return stockLotRepository.findByWarehouseCode(mainWarehouse.getCode()).stream()
                .filter(lot -> lot.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        lot -> lot.getItem().getId(),
                        Collectors.reducing(BigDecimal.ZERO,
                                lot -> lot.getQtyRemaining(),
                                BigDecimal::add)
                ));
    }

    private Map<UUID, BigDecimal> currentKitchenStock() {
        return stockLotRepository.findByWarehouseCode(KITCHEN_CODE).stream()
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

    // ── SEMI_PRODUCT helpers ──────────────────────────────────────────────────

    /**
     * Gợi ý số lượng BTP cần sản xuất dựa trên các Phiếu SX DAILY cho ngày đó.
     *
     * <p>Logic:
     * 1. Tìm tất cả PR DAILY của {@code date}
     * 2. Với mỗi line, expand recipe 1 tầng để tìm usage của {@code semiItemId}
     * 3. Trừ tồn KITCHEN hiện tại → suggested = max(0, needed - stock)
     *
     * @return Map có keys: neededByPlan, kitchenStock, suggested, unit
     */
    @Transactional(readOnly = true)
    public Map<String, Object> suggestSemiQty(UUID semiItemId, LocalDate date) {
        Item semiItem = itemRepository.findById(semiItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", semiItemId));

        // Tổng BTP cần theo các DAILY PR (không giới hạn status)
        List<ProductionRequest> dailyPrs = prRepository.findByProductionDate(date).stream()
                .filter(pr -> pr.getProductionType() == ProductionType.DAILY)
                .toList();

        BigDecimal neededByPlan = BigDecimal.ZERO;
        for (ProductionRequest pr : dailyPrs) {
            for (ProductionRequestLine l : pr.getLines()) {
                if (l.getProduct() == null) continue;
                Recipe recipe = recipeRepository.findByProductIdAndActiveTrue(l.getProduct().getId()).orElse(null);
                if (recipe == null) continue;
                List<RecipeLine> rls = recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
                for (RecipeLine rl : rls) {
                    if (rl.getItem() == null || !rl.getItem().getId().equals(semiItemId)) continue;
                    BigDecimal conv = resolveConversionFactor(rl.getUnit(), semiItem);
                    neededByPlan = neededByPlan.add(rl.getQuantity().multiply(l.getPlannedQty()).multiply(conv));
                }
            }
        }

        // Tồn KITCHEN của BTP này
        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));
        BigDecimal kitchenStock = stockLotRepository.findByItemIdAndWarehouseId(semiItemId, kitchen.getId())
                .stream()
                .filter(l -> l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .map(com.bakery.api.inventory.entity.StockLot::getQtyRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal suggested = neededByPlan.subtract(kitchenStock).max(BigDecimal.ZERO);

        return java.util.Map.of(
                "semiItemId", semiItemId,
                "semiItemName", semiItem.getName(),
                "unit", semiItem.getUnit() != null ? semiItem.getUnit() : "",
                "neededByPlan", neededByPlan,
                "kitchenStock", kitchenStock,
                "suggested", suggested
        );
    }

    /**
     * Tạo phiếu xuất kho MAIN → KITCHEN cho nguyên liệu cần để sản xuất BTP.
     *
     * <p>Chỉ tạo phiếu cho NL còn thiếu trong KITCHEN (không xuất những gì đã có đủ).
     * Nếu MAIN không đủ cho 1 NL, xuất tối đa tồn MAIN có thể.
     *
     * @param semiPrId ID Phiếu SX BTP (productionType = SEMI)
     * @return InventoryRequest đã tạo (PENDING_APPROVAL)
     */
    @Transactional
    public InventoryRequest generateSemiTransferRequest(UUID semiPrId) {
        ProductionRequest pr = prRepository.findById(semiPrId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionRequest", semiPrId));
        if (pr.getProductionType() != ProductionType.SEMI) {
            throw new IllegalStateException("Chỉ tạo phiếu NL cho Phiếu SX BTP (type=SEMI). Hiện tại: "
                    + pr.getProductionType());
        }

        // Expand BOM của tất cả lines trong phiếu SEMI
        Map<UUID, IngredientNeed> needed = new LinkedHashMap<>();
        for (ProductionRequestLine l : pr.getLines()) {
            if (l.getProduct() == null || l.getPlannedQty() == null) continue;
            Recipe recipe = recipeRepository.findBySemiProductIdAndActiveTrue(l.getProduct().getId()).orElse(null);
            if (recipe == null) {
                log.warn("generateSemiTransfer: BTP {} chưa có công thức — bỏ qua", l.getProduct().getCode());
                continue;
            }
            expandRecipe(recipe, l.getPlannedQty(), needed);
        }

        if (needed.isEmpty()) {
            throw new IllegalStateException("Không có NL nào (BTP chưa có công thức?).");
        }

        // So sánh với tồn KITCHEN — chỉ xuất phần thiếu
        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));
        Map<UUID, BigDecimal> kitchenStock = stockLotRepository.findByWarehouseCode(KITCHEN_CODE).stream()
                .filter(l -> l.getQtyRemaining().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        l -> l.getItem().getId(),
                        Collectors.reducing(BigDecimal.ZERO,
                                com.bakery.api.inventory.entity.StockLot::getQtyRemaining,
                                BigDecimal::add)));

        Warehouse mainWarehouse = findMainWarehouse();
        Map<UUID, BigDecimal> mainStock = currentMainStock(mainWarehouse);

        List<IngredientNeed> toTransfer = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (IngredientNeed need : needed.values()) {
            BigDecimal inKitchen = kitchenStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            BigDecimal deficit = need.totalQty.subtract(inKitchen);
            if (deficit.compareTo(BigDecimal.ZERO) <= 0) continue; // KITCHEN đã đủ

            BigDecimal inMain = mainStock.getOrDefault(need.item.getId(), BigDecimal.ZERO);
            if (inMain.compareTo(BigDecimal.ZERO) <= 0) {
                skipped.add(need.item.getName() + " (MAIN hết tồn)");
                continue;
            }
            BigDecimal transfer = deficit.min(inMain);
            toTransfer.add(new IngredientNeed(need.item, transfer));
        }

        if (toTransfer.isEmpty()) {
            throw new IllegalStateException(
                    "KITCHEN đã đủ nguyên liệu hoặc MAIN không có tồn."
                    + (skipped.isEmpty() ? "" : " NL thiếu MAIN: " + skipped));
        }

        // Tạo phiếu TRANSFER TR-SEMI-YYYYMMDD-NNN
        String dateStr = LocalDate.now().format(DATE_FMT);
        String codePrefix = "TR-SEMI-" + dateStr + "-";
        long count = inventoryRequestRepository.countByCodeStartingWith(codePrefix);

        InventoryRequest req = new InventoryRequest();
        req.setRequestType(InventoryRequestType.TRANSFER);
        req.setRequestDate(LocalDate.now());
        req.setExpectedDeliveryDate(pr.getProductionDate());
        req.setSourceWarehouse(mainWarehouse);
        req.setTargetWarehouse(kitchen);
        req.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        req.setCode(codePrefix + String.format("%03d", count + 1));
        req.setNote("Xuất NL cho Phiếu SX BTP " + pr.getCode()
                + (skipped.isEmpty() ? "" : " [bỏ qua " + skipped.size() + " NL thiếu MAIN]"));
        InventoryRequest saved = inventoryRequestRepository.save(req);

        int order = 1;
        for (IngredientNeed need : toTransfer) {
            InventoryRequestLine line = new InventoryRequestLine();
            line.setInventoryRequest(saved);
            line.setItem(need.item);
            line.setQuantity(need.totalQty);
            line.setUnit(need.item.getUnit() != null ? need.item.getUnit() : "kg");
            line.setSortOrder(order++);
            line.setNote("NL cho BTP — " + pr.getCode());
            line.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
            inventoryRequestLineRepository.save(line);
        }

        log.info("generateSemiTransfer: tạo phiếu {} ({} NL) cho phiếu BTP {}",
                saved.getCode(), toTransfer.size(), pr.getCode());
        return saved;
    }

    /**
     * Tính BTP (SEMI_PRODUCT) cần sản xuất từ base recipe của các nhóm FREE_GROUP/BATCH_FORMULA,
     * rồi so sánh với tồn KITCHEN để xác định còn thiếu bao nhiêu.
     *
     * <ul>
     *   <li><b>FREE_GROUP</b>: base_recipe × plannedQty (số cái target).</li>
     *   <li><b>BATCH_FORMULA</b>: base_recipe × plannedQty (số cối).</li>
     * </ul>
     *
     * @param kitchenStock tồn KITCHEN đã load (tái dụng từ checkIngredients, tránh query lại)
     * @return danh sách BTP cần, mỗi row: itemId, itemCode, itemName, unit, needed, inKitchen, shortage
     */
    private List<Map<String, Object>> buildSemiNeeds(ProductionPlan plan, Map<UUID, BigDecimal> kitchenStock) {
        Map<UUID, IngredientNeed> semiNeeded = computeSemiNeeds(plan);
        if (semiNeeded.isEmpty()) return List.of();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (IngredientNeed semiNeed : semiNeeded.values()) {
            BigDecimal inKitchen = kitchenStock.getOrDefault(semiNeed.item.getId(), BigDecimal.ZERO);
            BigDecimal gap = inKitchen.subtract(semiNeed.totalQty);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId",    semiNeed.item.getId());
            row.put("itemCode",  semiNeed.item.getCode());
            row.put("itemName",  semiNeed.item.getName());
            row.put("unit",      semiNeed.item.getUnit());
            row.put("needed",    semiNeed.totalQty);
            row.put("inKitchen", inKitchen);
            row.put("shortage",  gap.negate().max(BigDecimal.ZERO));
            rows.add(row);
        }

        // Sort: thiếu nhiều nhất lên trên; đủ thì theo tên
        rows.sort((a, b) -> {
            int bySh = ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage"));
            if (bySh != 0) return bySh;
            return String.valueOf(a.get("itemName")).compareTo(String.valueOf(b.get("itemName")));
        });
        return rows;
    }

    /**
     * Tính BTP (SEMI_PRODUCT) cần sản xuất cho từng nhóm FREE_GROUP/BATCH_FORMULA.
     *
     * <p>Có 2 cách setup base_recipe:
     * <ol>
     *   <li><b>Base recipe thuộc về SemiProduct</b> ({@code recipe.semiProduct != null}):
     *       Recipe đó MÔ TẢ cách làm 1 đơn vị BTP (ví dụ: "Cot Pana" recipe mô tả làm 1 cái Cot Pana).
     *       → BTP cần = SemiProduct đó × multiplier (1 BTP per piece/cối).
     *   </li>
     *   <li><b>Base recipe thuộc về Product / không có owner</b>: recipe có các dòng SEMI_PRODUCT.
     *       → Scan recipe lines, lấy SEMI_PRODUCT lines × multiplier.
     *   </li>
     * </ol>
     *
     * <p>Multiplier:
     * <ul>
     *   <li>FREE_GROUP: plannedQty (số cái target)</li>
     *   <li>BATCH_FORMULA: plannedQty (số cối)</li>
     * </ul>
     * Mỗi nhóm chỉ tính 1 lần.
     */
    private Map<UUID, IngredientNeed> computeSemiNeeds(ProductionPlan plan) {
        List<ProductionPlanLine> lines =
                planLineRepository.findByPlanIdOrderBySortOrderAsc(plan.getId());
        Map<UUID, IngredientNeed> result = new LinkedHashMap<>();
        java.util.Set<UUID> processedGroupIds = new java.util.HashSet<>();

        Map<UUID, Integer> plannedQtyByGroupId = planGroupRepository.findByPlanId(plan.getId()).stream()
                .collect(Collectors.toMap(
                        ppg -> ppg.getGroup().getId(),
                        ProductionPlanGroup::getPlannedQty));

        for (ProductionPlanLine planLine : lines) {
            ProductionGroup group = planLine.getGroup();
            if (group == null || group.getBaseRecipe() == null) continue;
            String gt = group.getGroupType();
            if (!"FREE_GROUP".equals(gt) && !"BATCH_FORMULA".equals(gt)) continue;
            if (!processedGroupIds.add(group.getId())) continue;

            BigDecimal multiplier;
            if (plannedQtyByGroupId.containsKey(group.getId())) {
                multiplier = BigDecimal.valueOf(plannedQtyByGroupId.get(group.getId()));
            } else {
                multiplier = resolveGroupTargetQty(group, plan.getPlanDate());
            }
            if (multiplier.compareTo(BigDecimal.ZERO) <= 0) continue;

            Recipe baseRecipe = group.getBaseRecipe();

            // ── Trường hợp 1: base_recipe thuộc về SemiProduct ──────────────────
            // Ví dụ: nhóm Pana dùng recipe "Cot Pana" (recipe.semiProduct = PANA_COT).
            // Recipe này MÔ TẢ cách làm 1 đơn vị Cot Pana.
            // → FREE_GROUP 40 cái = cần 40 Cot Pana; BATCH_FORMULA 1 cối = cần 1 Cối bắp.
            if (baseRecipe.getSemiProduct() != null) {
                SemiProduct semi = baseRecipe.getSemiProduct();
                result.merge(semi.getId(),
                        new IngredientNeed(semi, multiplier),
                        (existing, newNeed) -> {
                            existing.totalQty = existing.totalQty.add(newNeed.totalQty);
                            return existing;
                        });
                log.debug("computeSemiNeeds: nhóm {} ({}) cần {} {} '{}' (base recipe = recipe của BTP)",
                        group.getCode(), gt, multiplier, semi.getUnit(), semi.getName());
                continue;
            }

            // ── Trường hợp 2: base_recipe có dòng SEMI_PRODUCT ──────────────────
            // Ví dụ: recipe của group liệt kê "Cot Pana" là nguyên liệu → lấy dòng đó.
            List<RecipeLine> recipeLines = recipeLineRepository
                    .findByRecipeIdOrderBySortOrderAsc(baseRecipe.getId());
            for (RecipeLine rl : recipeLines) {
                if (rl.getItem() == null || !(rl.getItem() instanceof SemiProduct semi)) continue;

                BigDecimal conv = resolveConversionFactor(rl.getUnit(), semi);
                BigDecimal needed = rl.getQuantity().multiply(multiplier).multiply(conv);

                result.merge(semi.getId(),
                        new IngredientNeed(semi, needed),
                        (existing, newNeed) -> {
                            existing.totalQty = existing.totalQty.add(newNeed.totalQty);
                            return existing;
                        });
                log.debug("computeSemiNeeds: nhóm {} ({}) cần {} {} '{}' (từ dòng recipe)",
                        group.getCode(), gt, needed, semi.getUnit(), semi.getName());
            }
        }
        return result;
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
