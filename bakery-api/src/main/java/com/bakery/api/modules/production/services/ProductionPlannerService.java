package com.bakery.api.modules.production.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.production.entities.BatchFormulaConfig;
import com.bakery.api.modules.production.entities.ProductionGroup;
import com.bakery.api.modules.production.entities.ProductionPlan;
import com.bakery.api.modules.production.entities.ProductionPlanLine;
import com.bakery.api.modules.production.repositories.BatchFormulaConfigRepository;
import com.bakery.api.modules.production.repositories.ProductionGroupRepository;
import com.bakery.api.modules.production.repositories.ProductionPlanRepository;
import com.bakery.api.modules.sales.entities.DailyInventory;
import com.bakery.api.modules.sales.repositories.CustomerOrderRepository;
import com.bakery.api.modules.sales.repositories.DailyInventoryRepository;

/**
 * Production Planning Engine — 3 patterns:
 *
 *   A. GROUP_SUBTRACT  — nhóm sản phẩm dùng chung phôi (VD: Pana)
 *   B. LAN_MAM         — tính số mâm theo ceil (VD: Bento, tối đa 12/mâm)
 *   C. LAN_XUAT        — ma trận cối bông lan theo size (VD: PK-SIZE-*)
 *
 * Flow tổng quát (gọi từ ProductionController):
 *   generateDailyPlan(targetDate) →
 *     1. Chạy GROUP_SUBTRACT → ghi ProductionPlan lines cho phôi
 *     2. Chạy LAN_MAM        → ghi ProductionPlan lines cho mâm Bento
 *     3. Chạy LAN_XUAT       → ghi ProductionPlan lines cho cối bông lan
 *     4. Feed đơn SHEET_CAKE pending → cộng vào GROUP_SUBTRACT phôi
 *
 * Input date = ngày kế hoạch (ngày mai). Tồn kho lấy từ daily_inventory ngày hôm qua.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionPlannerService {

    private final ProductionGroupRepository       productionGroupRepository;
    private final BatchFormulaConfigRepository    batchFormulaConfigRepository;
    private final DailyInventoryRepository        dailyInventoryRepository;
    private final ProductionPlanRepository        productionPlanRepository;
    private final BranchRepository                branchRepository;
    private final CustomerOrderRepository         customerOrderRepository;

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Tạo production plan cho targetDate. Idempotent — nếu plan đã tồn tại thì override.
     *
     * @param targetDate ngày kế hoạch (thường = today + 1)
     * @param createdBy  người tạo kế hoạch
     * @return plan đã lưu với đầy đủ lines
     */
    @Transactional
    public ProductionPlan generateDailyPlan(LocalDate targetDate, String createdBy) {
        log.info("▶ ProductionPlannerService.generateDailyPlan({})", targetDate);

        // Xóa plan cũ nếu có (idempotent)
        productionPlanRepository.findByPlanDate(targetDate).ifPresent(existing -> {
            log.warn("⚠ Plan {} đã tồn tại — override", targetDate);
            productionPlanRepository.delete(existing);
        });

        ProductionPlan plan = ProductionPlan.builder()
                .planDate(targetDate)
                .status("PENDING")
                .createdBy(createdBy)
                .build();

        List<ProductionPlanLine> lines = new ArrayList<>();

        // Inventory reference = ngày hôm qua
        LocalDate inventoryDate = targetDate.minusDays(1);
        boolean isWeekend = targetDate.getDayOfWeek() == DayOfWeek.SATURDAY
                         || targetDate.getDayOfWeek() == DayOfWeek.SUNDAY;

        // 1. Pattern A: GROUP_SUBTRACT
        lines.addAll(runGroupSubtract(targetDate, inventoryDate, isWeekend, plan));

        // 2. Pattern B: LAN_MAM
        lines.addAll(runLanMam(inventoryDate, isWeekend, plan));

        // 3. Pattern C: LAN_XUAT
        lines.addAll(runLanXuat(inventoryDate, isWeekend, plan));

        plan.setLines(lines);
        ProductionPlan saved = productionPlanRepository.save(plan);
        log.info("✓ Plan {} tạo xong — {} lines", targetDate, lines.size());
        return saved;
    }

    // =========================================================================
    // PATTERN A — GROUP_SUBTRACT
    // =========================================================================

    /**
     * Tính số phôi cần làm cho từng nhóm sản phẩm dùng chung phôi.
     *
     * Logic:
     *   target_nhóm     = weekday_target hoặc weekend_target của group
     *   tồn_nhóm        = Σ qty_closing (daily_inventory ngày hôm qua) các product trong nhóm
     *   phôi_cần_làm    = max(0, target_nhóm − tồn_nhóm)
     *
     * Thêm vào đó: cộng nhu cầu từ SHEET_CAKE orders có delivery_date = targetDate
     * vào target của nhóm tương ứng trước khi tính.
     */
    public Map<String, Integer> calculateRequiredSemiProducts(LocalDate targetDate, LocalDate inventoryDate, boolean isWeekend) {
        List<ProductionGroup> groups = productionGroupRepository.findAllByActiveTrue();
        Map<String, Integer> result = new LinkedHashMap<>();

        // SHEET_CAKE orders cho ngày mai — cộng thêm vào target
        Map<UUID, Integer> sheetCakeExtra = getSheetCakeExtra(targetDate);

        for (ProductionGroup group : groups) {
            BigDecimal target = isWeekend ? group.getWeekendTarget() : group.getWeekdayTarget();

            // Cộng thêm SHEET_CAKE demand cho các product trong nhóm
            List<UUID> memberProductIds = productionGroupRepository.findProductIdsByGroupId(group.getId());
            int extraFromOrders = memberProductIds.stream()
                    .mapToInt(pid -> sheetCakeExtra.getOrDefault(pid, 0))
                    .sum();
            target = target.add(BigDecimal.valueOf(extraFromOrders));

            // Tổng tồn kho nhóm
            BigDecimal totalStock = BigDecimal.ZERO;
            for (UUID productId : memberProductIds) {
                totalStock = totalStock.add(
                    getQtyClosing(productId, inventoryDate)
                );
            }

            int needed = Math.max(0, target.subtract(totalStock).intValue());
            result.put(group.getMainSemiProductCode(), needed);

            log.debug("GROUP_SUBTRACT [{}]: target={} stock={} → cần {} phôi {}",
                    group.getGroupCode(), target, totalStock, needed, group.getMainSemiProductCode());
        }
        return result;
    }

    // =========================================================================
    // PATTERN B — LAN_MAM (Bento)
    // =========================================================================

    /**
     * Tính số mâm cần làm theo ceil.
     *
     * Công thức:
     *   tổng_demand = production_request / customer_order cho targetDate
     *   tổng_tồn   = Σ qty_closing (daily_inventory) của các product thuộc prefix
     *   cần_thêm   = max(0, tổng_demand − tổng_tồn)
     *   số_mâm     = ceil(cần_thêm / max_qty_per_batch)
     */
    public int calculateBentoBatches(int totalBentoDemand, BatchFormulaConfig config) {
        int maxPerBatch = config.getMaxQtyPerBatch();
        if (totalBentoDemand <= 0) return 0;
        int batches = (int) Math.ceil((double) totalBentoDemand / maxPerBatch);
        log.debug("LAN_MAM [{}]: demand={} maxPerBatch={} → {} mâm",
                config.getFormulaCode(), totalBentoDemand, maxPerBatch, batches);
        return batches;
    }

    // =========================================================================
    // PATTERN C — LAN_XUAT (Matrix cối bông lan)
    // =========================================================================

    /**
     * Tính số cối bông lan và yield ra từng size.
     *
     * Công thức per size:
     *   cần_thêm_size = max(0, target_size − tồn_size)
     *   tổng_nhu_cầu  = Σ (cần_thêm_size × multiplier) + số_cối_bắp (manual input)
     *   số_cối        = ceil(tổng_nhu_cầu / max_qty_per_batch)
     *   output        = số_cối × output_yield_mapping
     *
     * @param cotBap số cối bánh bắp thêm trong ngày (manual input từ NV)
     * @return map product_code → số lượng kế hoạch
     */
    public Map<String, Integer> calculateLanXuat(
            LocalDate inventoryDate,
            boolean isWeekend,
            BatchFormulaConfig config,
            int cotBap) {

        double multiplier = getMultiplier(config);
        Map<String, Object> yieldMap = config.getOutputYieldMapping();

        // Tính tổng nhu cầu quy đổi sang cối
        double totalCoi = 0.0;

        // Lấy production_template cho các product thuộc prefix để biết target
        // (ở đây dùng weekday_qty/weekend_qty từ production_template)
        // Tính Σ (PK+PL) × multiplier per size từ target và tồn
        for (String productCode : yieldMap.keySet()) {
            BigDecimal stock = getQtyClosingByCode(productCode, inventoryDate);
            // target từ production_template — nếu không có thì 0
            BigDecimal target = BigDecimal.ZERO; // TODO: load từ production_template
            double need = Math.max(0, target.subtract(stock).doubleValue());
            totalCoi += need * multiplier;
        }

        // Cộng thêm cối bắp
        totalCoi += cotBap;

        int soCoiThucTe = (int) Math.ceil(totalCoi);
        log.debug("LAN_XUAT [{}]: totalCoi={} cotBap={} → {} cối",
                config.getFormulaCode(), totalCoi, cotBap, soCoiThucTe);

        // Map output
        Map<String, Integer> output = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : yieldMap.entrySet()) {
            int yieldPerCoi = ((Number) entry.getValue()).intValue();
            output.put(entry.getKey(), soCoiThucTe * yieldPerCoi);
        }
        return output;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private List<ProductionPlanLine> runGroupSubtract(
            LocalDate targetDate, LocalDate inventoryDate, boolean isWeekend, ProductionPlan plan) {

        Map<String, Integer> semiNeeds = calculateRequiredSemiProducts(targetDate, inventoryDate, isWeekend);
        List<ProductionPlanLine> lines = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : semiNeeds.entrySet()) {
            if (entry.getValue() <= 0) continue;
            // Ghi plan line với note là semi_product_code (không FK trực tiếp vì plan_line → product)
            // Trong thực tế bếp nhận lệnh "làm X phôi SF-xxx"
            ProductionPlanLine line = ProductionPlanLine.builder()
                    .plan(plan)
                    .qtyPlanned(BigDecimal.valueOf(entry.getValue()))
                    .note("GROUP_SUBTRACT | semi: " + entry.getKey())
                    .build();
            lines.add(line);
        }
        return lines;
    }

    private List<ProductionPlanLine> runLanMam(LocalDate inventoryDate, boolean isWeekend, ProductionPlan plan) {
        List<BatchFormulaConfig> configs = batchFormulaConfigRepository
                .findAllByFormulaTypeAndActiveTrue("LAN_MAM");
        List<ProductionPlanLine> lines = new ArrayList<>();

        for (BatchFormulaConfig config : configs) {
            // Lấy tổng tồn kho các product thuộc prefix
            BigDecimal totalStock = BigDecimal.ZERO; // TODO: query by prefix
            int totalDemand = 0;                     // TODO: load từ production_template / request
            int demand = Math.max(0, totalDemand - totalStock.intValue());
            int soMam = calculateBentoBatches(demand, config);
            if (soMam <= 0) continue;

            ProductionPlanLine line = ProductionPlanLine.builder()
                    .plan(plan)
                    .qtyPlanned(BigDecimal.valueOf(soMam))
                    .note("LAN_MAM | formula: " + config.getFormulaCode()
                          + " | " + soMam + " mâm × " + config.getMaxQtyPerBatch() + " cái/mâm")
                    .build();
            lines.add(line);
        }
        return lines;
    }

    private List<ProductionPlanLine> runLanXuat(LocalDate inventoryDate, boolean isWeekend, ProductionPlan plan) {
        List<BatchFormulaConfig> configs = batchFormulaConfigRepository
                .findAllByFormulaTypeAndActiveTrue("LAN_XUAT");
        List<ProductionPlanLine> lines = new ArrayList<>();

        for (BatchFormulaConfig config : configs) {
            // cotBap = 0 mặc định, NV nhập thêm qua API nếu cần
            Map<String, Integer> output = calculateLanXuat(inventoryDate, isWeekend, config, 0);

            for (Map.Entry<String, Integer> entry : output.entrySet()) {
                if (entry.getValue() <= 0) continue;
                ProductionPlanLine line = ProductionPlanLine.builder()
                        .plan(plan)
                        .qtyPlanned(BigDecimal.valueOf(entry.getValue()))
                        .note("LAN_XUAT | formula: " + config.getFormulaCode()
                              + " | product: " + entry.getKey())
                        .build();
                lines.add(line);
            }
        }
        return lines;
    }

    /** qty_closing của 1 product (by UUID) ngày inventoryDate, từ branch is_main */
    private BigDecimal getQtyClosing(UUID productId, LocalDate inventoryDate) {
        Branch mainBranch = branchRepository.findByIsMainTrue()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy main branch"));
        return dailyInventoryRepository
                .findByBranchIdAndProductIdAndInventoryDate(mainBranch.getId(), productId, inventoryDate)
                .map(DailyInventory::getQtyClosing)
                .orElse(BigDecimal.ZERO);
    }

    /** qty_closing của 1 product (by code) — dùng trong LAN_XUAT */
    private BigDecimal getQtyClosingByCode(String productCode, LocalDate inventoryDate) {
        // Simplified — trong thực tế cần thêm query findByProductCode
        return BigDecimal.ZERO;
    }

    /** SHEET_CAKE orders có delivery_date = targetDate → map product_id → qty */
    private Map<UUID, Integer> getSheetCakeExtra(LocalDate targetDate) {
        return customerOrderRepository
                .findAllByDeliveryDateAndStatus(targetDate, "CONFIRMED").stream()
                .flatMap(order -> order.getLines().stream())
                .filter(line -> line.getProduct() != null
                        && "SHEET_CAKE".equals(line.getProduct().getProductType() != null
                                ? line.getProduct().getProductType().name() : ""))
                .collect(Collectors.toMap(
                        line -> line.getProduct().getId(),
                        line -> line.getQty().intValue(),
                        Integer::sum
                ));
    }

    private double getMultiplier(BatchFormulaConfig config) {
        if (config.getInputVariables() == null) return 1.0;
        Object m = config.getInputVariables().get("multiplier");
        return m != null ? ((Number) m).doubleValue() : 1.0;
    }
}
