package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.api.production.entity.ProductionGroupItem;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.api.production.entity.ProductionThresholdRule;
import com.bakery.api.production.repository.ProductionGroupRepository;
import com.bakery.api.production.repository.ProductionPlanLineRepository;
import com.bakery.api.production.repository.ProductionPlanRepository;
import com.bakery.api.production.repository.ProductionThresholdRuleRepository;
import com.bakery.api.report.entity.DailyReport;
import com.bakery.api.report.entity.DailyReportLine;
import com.bakery.api.report.repository.DailyReportLineRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo kế hoạch sản xuất ngày mai dựa trên tồn kho cuối ngày hôm nay.
 *
 * <p>Ba pattern:
 * <ul>
 *   <li><b>SIMPLE</b>: mỗi Product có threshold rules riêng.</li>
 *   <li><b>FREE_GROUP</b>: nhóm products cùng target tổng (ví dụ: Pana Cotta).</li>
 *   <li><b>BATCH_FORMULA</b>: nhóm theo cối, mỗi size có gram/cái (ví dụ: Bánh Bắp).</li>
 * </ul>
 *
 * <p>Được gọi tự động khi {@code DailyReportService.finalize()} hoàn thành.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionPlannerService {

    private final ProductionPlanRepository planRepository;
    private final ProductionPlanLineRepository lineRepository;
    private final ProductionThresholdRuleRepository ruleRepository;
    private final ProductionGroupRepository groupRepository;
    private final DailyReportLineRepository reportLineRepository;
    private final ItemLookupRepository itemRepository;

    /**
     * Tạo kế hoạch thủ công cho ngày bất kỳ (không cần DailyReport).
     * Tồn kho = 0 cho tất cả sản phẩm — manager tự điều chỉnh sau.
     * Idempotent: nếu đã có plan cho ngày đó → trả về plan hiện tại.
     */
    @Transactional
    public ProductionPlan generateForDate(LocalDate planDate) {
        if (planRepository.existsByPlanDate(planDate)) {
            log.info("Plan cho ngày {} đã tồn tại, trả về plan hiện tại.", planDate);
            ProductionPlan existing = planRepository.findByPlanDate(planDate).orElseThrow();
            initPlanLines(existing);
            return existing;
        }

        DayType dayType = resolveDayType(planDate);
        Map<UUID, BigDecimal> emptyRemaining = Map.of();

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanDate(planDate);
        plan.setDayType(dayType);
        plan.setApprovalStatus(ApprovalStatus.DRAFT);

        List<ProductionPlanLine> lines = new ArrayList<>();
        Set<UUID> groupedItemIds = new HashSet<>();
        List<ProductionGroup> activeGroups = groupRepository.findByActiveTrue();
        for (ProductionGroup group : activeGroups) {
            group.getItems().forEach(gi -> groupedItemIds.add(gi.getItem().getId()));
        }

        int sortOrder = 0;
        for (ProductionGroup group : activeGroups) {
            if ("FREE_GROUP".equals(group.getGroupType())) {
                lines.addAll(buildFreeGroupLines(plan, group, dayType, emptyRemaining, sortOrder));
            } else if ("BATCH_FORMULA".equals(group.getGroupType())) {
                lines.addAll(buildBatchFormulaLines(plan, group, emptyRemaining, sortOrder));
            }
            sortOrder += group.getItems().size() + 1;
        }

        List<Item> allProducts = itemRepository.findAll().stream()
                .filter(i -> i instanceof Product)
                .filter(i -> !groupedItemIds.contains(i.getId()))
                .toList();

        for (Item item : allProducts) {
            List<ProductionThresholdRule> rules = ruleRepository
                    .findByItemIdAndDayTypeOrderBySortOrderAsc(item.getId(), dayType);
            if (rules.isEmpty()) continue;
            ProductionPlanLine line = buildSimpleLine(plan, item, BigDecimal.ZERO, rules, sortOrder++);
            if (line != null) lines.add(line);
        }

        plan.getLines().addAll(lines);
        ProductionPlan saved = planRepository.save(plan);
        initPlanLines(saved);
        return saved;
    }

    /**
     * Tạo kế hoạch sản xuất DRAFT cho ngày {@code planDate}.
     *
     * <p>Nếu đã có plan cho ngày đó → skip (idempotent).
     *
     * @param dailyReport DailyReport vừa được finalize (nguồn tồn kho)
     * @return plan mới tạo, hoặc plan đã tồn tại
     */
    @Transactional
    public ProductionPlan generateDraft(DailyReport dailyReport) {
        LocalDate planDate = dailyReport.getReportDate().plusDays(1);

        // Idempotent — không tạo 2 lần cho cùng 1 ngày
        if (planRepository.existsByPlanDate(planDate)) {
            log.info("Plan cho ngày {} đã tồn tại, bỏ qua.", planDate);
            return planRepository.findByPlanDate(planDate).orElseThrow();
        }

        DayType dayType = resolveDayType(planDate);

        // Map itemId → qtyRemainingActual từ DailyReport hôm nay
        List<DailyReportLine> reportLines = reportLineRepository
                .findByDailyReportId(dailyReport.getId());
        Map<UUID, BigDecimal> remainingByItem = reportLines.stream()
                .filter(l -> l.getQtyRemainingActual() != null)
                .collect(Collectors.toMap(
                        l -> l.getItem().getId(),
                        DailyReportLine::getQtyRemainingActual,
                        BigDecimal::add));

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanDate(planDate);
        plan.setDayType(dayType);
        plan.setGeneratedFrom(dailyReport);
        plan.setApprovalStatus(ApprovalStatus.DRAFT);

        List<ProductionPlanLine> lines = new ArrayList<>();

        // ── Xác định items nào thuộc group ─────────────────────────────
        Set<UUID> groupedItemIds = new HashSet<>();
        List<ProductionGroup> activeGroups = groupRepository.findByActiveTrue();
        for (ProductionGroup group : activeGroups) {
            group.getItems().forEach(gi -> groupedItemIds.add(gi.getItem().getId()));
        }

        // ── Pattern 2 & 3: Group-based items ──────────────────────────
        int sortOrder = 0;
        for (ProductionGroup group : activeGroups) {
            if ("FREE_GROUP".equals(group.getGroupType())) {
                lines.addAll(buildFreeGroupLines(plan, group, dayType, remainingByItem, sortOrder));
            } else if ("BATCH_FORMULA".equals(group.getGroupType())) {
                lines.addAll(buildBatchFormulaLines(plan, group, remainingByItem, sortOrder));
            }
            sortOrder += group.getItems().size() + 1;
        }

        // ── Pattern 1: SIMPLE — các items không thuộc group nào ────────
        List<Item> allProducts = itemRepository.findAll().stream()
                .filter(i -> i instanceof Product)
                .filter(i -> !groupedItemIds.contains(i.getId()))
                .toList();

        for (Item item : allProducts) {
            BigDecimal remaining = remainingByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            List<ProductionThresholdRule> rules = ruleRepository
                    .findByItemIdAndDayTypeOrderBySortOrderAsc(item.getId(), dayType);

            if (rules.isEmpty()) continue; // sản phẩm chưa config rule → bỏ qua

            ProductionPlanLine line = buildSimpleLine(plan, item, remaining, rules, sortOrder++);
            if (line != null) {
                lines.add(line);
            }
        }

        plan.getLines().addAll(lines);
        ProductionPlan savedDraft = planRepository.save(plan);
        initPlanLines(savedDraft);
        return savedDraft;
    }

    // ── Pattern 1: SIMPLE ────────────────────────────────────────────────────

    private ProductionPlanLine buildSimpleLine(
            ProductionPlan plan, Item item, BigDecimal remaining,
            List<ProductionThresholdRule> rules, int sortOrder) {

        for (ProductionThresholdRule rule : rules) {
            if (matchesCondition(remaining, rule)) {
                int suggestedQty = calcSuggestedQty(remaining, rule);
                if (suggestedQty <= 0) continue;

                ProductionPlanLine line = new ProductionPlanLine();
                line.setPlan(plan);
                line.setItem(item);
                line.setPlanType("SIMPLE");
                line.setQtyRemaining(remaining);
                line.setSuggestedQty(suggestedQty);
                line.setRuleNote(buildRuleNote(remaining, rule, suggestedQty));
                line.setSortOrder(sortOrder);
                return line;
            }
        }
        return null;
    }

    /**
     * Kiểm tra ngưỡng kích hoạt:
     * COUNT   — remaining < conditionValue
     * PERCENT — remaining < conditionValue% × actionValue
     */
    private boolean matchesCondition(BigDecimal remaining, ProductionThresholdRule rule) {
        BigDecimal threshold = "PERCENT".equals(rule.getConditionType())
                ? rule.getConditionValue()
                        .multiply(BigDecimal.valueOf(rule.getActionValue()))
                        .divide(BigDecimal.valueOf(100))
                : rule.getConditionValue();
        return remaining.compareTo(threshold) < 0;
    }

    /**
     * Tính số lượng cần sản xuất:
     * PRODUCE_MORE   — luôn là actionValue
     * FILL_TO_TARGET — max(0, actionValue − remaining)
     */
    private int calcSuggestedQty(BigDecimal remaining, ProductionThresholdRule rule) {
        if ("FILL_TO_TARGET".equals(rule.getActionType())) {
            int needed = rule.getActionValue() - remaining.intValue();
            return Math.max(0, needed);
        }
        return rule.getActionValue(); // PRODUCE_MORE
    }

    private String buildRuleNote(BigDecimal remaining, ProductionThresholdRule rule, int suggestedQty) {
        String condDesc = "PERCENT".equals(rule.getConditionType())
                ? String.format("%s%% × %d = %.1f",
                        rule.getConditionValue().toPlainString(),
                        rule.getActionValue(),
                        rule.getConditionValue().multiply(BigDecimal.valueOf(rule.getActionValue()))
                                .divide(BigDecimal.valueOf(100)).doubleValue())
                : rule.getConditionValue().toPlainString();
        String actionDesc = "FILL_TO_TARGET".equals(rule.getActionType())
                ? String.format("bù đủ %d", rule.getActionValue())
                : String.format("làm thêm %d", rule.getActionValue());
        return String.format("Còn %s < %s → %s = %d",
                remaining.toPlainString(), condDesc, actionDesc, suggestedQty);
    }

    // ── Pattern 2: FREE_GROUP ────────────────────────────────────────────────

    private List<ProductionPlanLine> buildFreeGroupLines(
            ProductionPlan plan, ProductionGroup group, DayType dayType,
            Map<UUID, BigDecimal> remainingByItem, int startSortOrder) {

        int target = dayType == DayType.WEEKDAY
                ? (group.getTargetWeekday() != null ? group.getTargetWeekday() : 0)
                : (group.getTargetWeekend() != null ? group.getTargetWeekend() : 0);

        BigDecimal totalRemaining = group.getItems().stream()
                .map(gi -> remainingByItem.getOrDefault(gi.getItem().getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Kiểm tra thresholdPercent: nếu set, chỉ sản xuất khi tổng tồn < threshold% × target
        if (group.getThresholdPercent() != null && target > 0) {
            BigDecimal thresholdQty = BigDecimal.valueOf(group.getThresholdPercent())
                    .multiply(BigDecimal.valueOf(target))
                    .divide(BigDecimal.valueOf(100));
            if (totalRemaining.compareTo(thresholdQty) >= 0) {
                log.info("Nhóm {} bỏ qua: tổng tồn {} >= {}% × {} = {}",
                        group.getCode(), totalRemaining, group.getThresholdPercent(), target, thresholdQty);
                return List.of();
            }
        }

        int totalNeeded = Math.max(0, target - totalRemaining.intValue());
        String thresholdNote = group.getThresholdPercent() != null
                ? String.format(" [ngưỡng %d%%]", group.getThresholdPercent())
                : "";

        List<ProductionPlanLine> lines = new ArrayList<>();
        int i = 0;
        for (ProductionGroupItem gi : group.getItems()) {
            Item item = gi.getItem();
            BigDecimal remaining = remainingByItem.getOrDefault(item.getId(), BigDecimal.ZERO);

            ProductionPlanLine line = new ProductionPlanLine();
            line.setPlan(plan);
            line.setItem(item);
            line.setPlanType("FREE_GROUP");
            line.setGroup(group);
            line.setQtyRemaining(remaining);
            // suggestedQty = null cho FREE_GROUP — nhân viên tự phân bổ
            line.setSuggestedQty(null);
            line.setRuleNote(String.format(
                    "Nhóm %s%s: tổng còn %s, target %d → cần thêm %d (nhân viên phân bổ)",
                    group.getName(), thresholdNote, totalRemaining.toPlainString(), target, totalNeeded));
            line.setSortOrder(startSortOrder + i++);
            lines.add(line);
        }

        return lines;
    }

    // ── Pattern 3: BATCH_FORMULA ─────────────────────────────────────────────

    private List<ProductionPlanLine> buildBatchFormulaLines(
            ProductionPlan plan, ProductionGroup group,
            Map<UUID, BigDecimal> remainingByItem, int startSortOrder) {

        // Tổng gram còn lại của tất cả sizes
        BigDecimal totalRemainingGrams = group.getItems().stream()
                .filter(gi -> gi.getGramsPerUnit() != null)
                .map(gi -> {
                    BigDecimal remaining = remainingByItem
                            .getOrDefault(gi.getItem().getId(), BigDecimal.ZERO);
                    return remaining.multiply(gi.getGramsPerUnit());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int batchWeight = group.getBatchWeightGrams() != null ? group.getBatchWeightGrams() : 1;

        // Số cối gợi ý: làm đủ để tổng tồn kho + làm mới >= 2 cối
        // Logic đơn giản: gợi ý 2 cối nếu còn < 1 cối, 1 cối nếu còn < 2 cối
        int batches;
        double remainingBatches = totalRemainingGrams.doubleValue() / batchWeight;
        if (remainingBatches < 1.0) {
            batches = 2;
        } else if (remainingBatches < 2.0) {
            batches = 1;
        } else {
            batches = 0; // đủ rồi
        }

        List<ProductionPlanLine> lines = new ArrayList<>();
        int i = 0;
        for (ProductionGroupItem gi : group.getItems()) {
            Item item = gi.getItem();
            BigDecimal remaining = remainingByItem.getOrDefault(item.getId(), BigDecimal.ZERO);

            ProductionPlanLine line = new ProductionPlanLine();
            line.setPlan(plan);
            line.setItem(item);
            line.setPlanType("BATCH_FORMULA");
            line.setGroup(group);
            line.setQtyRemaining(remaining);
            line.setGramsPerUnit(gi.getGramsPerUnit());
            // suggestedQty = null — nhân viên quyết định phân bổ theo cối
            line.setSuggestedQty(null);
            line.setRuleNote(String.format(
                    "Nhóm %s: còn %.1f cối, gợi ý làm %d cối (%dkg). Nhân viên phân bổ size.",
                    group.getName(), remainingBatches, batches, batches * batchWeight / 1000));
            line.setSortOrder(startSortOrder + i++);
            lines.add(line);
        }

        return lines;
    }

    // ── Regenerate (kế hoạch bị REJECTED) ────────────────────────────────────

    /**
     * Xóa các line cũ của plan REJECTED, tạo lại DRAFT với tồn kho = 0.
     * Dùng khi manager reject nhầm hoặc muốn tạo lại kế hoạch mới cho ngày đó.
     */
    @Transactional
    public ProductionPlan regenerateRejected(UUID planId) {
        ProductionPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", planId));
        if (plan.getApprovalStatus() != ApprovalStatus.REJECTED) {
            throw new IllegalStateException(
                    "Chỉ có thể tạo lại kế hoạch ở trạng thái REJECTED. Hiện tại: "
                            + plan.getApprovalStatus());
        }

        // Xóa lines cũ ngay xuống DB — dùng @Modifying JPQL để flush trước khi INSERT mới
        // (plan.getLines().clear() không flush ngay, gây duplicate key khi insert batch)
        lineRepository.deleteAllByPlanId(plan.getId());
        plan.getLines().clear(); // sync in-memory collection

        LocalDate planDate = plan.getPlanDate();
        DayType dayType = resolveDayType(planDate);
        plan.setDayType(dayType);
        plan.setApprovalStatus(ApprovalStatus.DRAFT);
        plan.setRejectedReason(null);

        // Tạo lại lines với tồn kho = 0 (giống generateForDate)
        Map<UUID, BigDecimal> emptyRemaining = Map.of();
        List<ProductionPlanLine> lines = new ArrayList<>();
        Set<UUID> groupedItemIds = new HashSet<>();
        List<ProductionGroup> activeGroups = groupRepository.findByActiveTrue();
        for (ProductionGroup group : activeGroups) {
            group.getItems().forEach(gi -> groupedItemIds.add(gi.getItem().getId()));
        }

        int sortOrder = 0;
        for (ProductionGroup group : activeGroups) {
            if ("FREE_GROUP".equals(group.getGroupType())) {
                lines.addAll(buildFreeGroupLines(plan, group, dayType, emptyRemaining, sortOrder));
            } else if ("BATCH_FORMULA".equals(group.getGroupType())) {
                lines.addAll(buildBatchFormulaLines(plan, group, emptyRemaining, sortOrder));
            }
            sortOrder += group.getItems().size() + 1;
        }

        List<Item> allProducts = itemRepository.findAll().stream()
                .filter(i -> i instanceof Product)
                .filter(i -> !groupedItemIds.contains(i.getId()))
                .toList();

        for (Item item : allProducts) {
            List<ProductionThresholdRule> rules =
                    ruleRepository.findByItemIdAndDayTypeOrderBySortOrderAsc(item.getId(), dayType);
            if (rules.isEmpty()) continue;
            ProductionPlanLine line = buildSimpleLine(plan, item, BigDecimal.ZERO, rules, sortOrder++);
            if (line != null) lines.add(line);
        }

        plan.getLines().addAll(lines);
        ProductionPlan saved = planRepository.save(plan);
        initPlanLines(saved);
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Force-initialize tất cả lazy proxies bên trong plan.lines trong khi còn trong transaction.
     * Gọi trước khi return khỏi @Transactional để controller có thể gọi DTO.from() an toàn.
     */
    private void initPlanLines(ProductionPlan plan) {
        plan.getLines().forEach(l -> {
            if (l.getItem() != null) l.getItem().getCode();   // init Item proxy
            if (l.getGroup() != null) l.getGroup().getCode(); // init ProductionGroup proxy
        });
    }

    private DayType resolveDayType(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
                ? DayType.WEEKEND
                : DayType.WEEKDAY;
    }
}
