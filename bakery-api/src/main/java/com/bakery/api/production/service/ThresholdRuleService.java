package com.bakery.api.production.service;

import java.util.List;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.production.dto.ThresholdRuleRequest;
import com.bakery.api.production.entity.ProductionThresholdRule;
import com.bakery.api.production.repository.ProductionThresholdRuleRepository;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThresholdRuleService {

    private final ProductionThresholdRuleRepository ruleRepository;
    private final ItemLookupRepository itemRepository;

    /** Lấy tất cả rules của 1 item (cả WEEKDAY + WEEKEND). */
    @Transactional(readOnly = true)
    public List<ProductionThresholdRule> findByItem(UUID itemId) {
        List<ProductionThresholdRule> weekday =
                ruleRepository.findByItemIdAndDayTypeOrderBySortOrderAsc(itemId, DayType.WEEKDAY);
        List<ProductionThresholdRule> weekend =
                ruleRepository.findByItemIdAndDayTypeOrderBySortOrderAsc(itemId, DayType.WEEKEND);
        return java.util.stream.Stream.concat(weekday.stream(), weekend.stream()).toList();
    }

    /**
     * Replace toàn bộ rules của item — xóa cũ, thêm mới.
     * Cho phép gửi lại toàn bộ config (idempotent replace).
     */
    @Transactional
    public List<ProductionThresholdRule> replaceRules(UUID itemId, ThresholdRuleRequest req) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        ruleRepository.deleteAllByItemId(itemId);

        List<ProductionThresholdRule> newRules = req.rules().stream()
                .map(r -> {
                    ProductionThresholdRule rule = new ProductionThresholdRule();
                    rule.setItem(item);
                    rule.setDayType(DayType.valueOf(r.dayType().toUpperCase()));
                    rule.setSortOrder(r.sortOrder());
                    rule.setConditionType(r.conditionType().toUpperCase());
                    rule.setConditionValue(r.conditionValue());
                    rule.setActionType(r.actionType().toUpperCase());
                    rule.setActionValue(r.actionValue());
                    return rule;
                })
                .toList();

        return ruleRepository.saveAll(newRules);
    }
}
