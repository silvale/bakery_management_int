/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.recipe.service.RecipeCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper tách biệt transaction cho cost recalculation.
 *
 * <p>Chạy trong transaction REQUIRES_NEW — nếu tính toán thất bại thì chỉ
 * transaction này rollback, không ảnh hưởng outer transaction của approve().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCostHelper {

    private final RecipeCostService recipeCostService;
    private final ItemLookupRepository repository;

    /**
     * Tính lại unit_cost và persist — trong transaction độc lập.
     * Mọi exception đều được swallow để approve() không bị ảnh hưởng.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recalculateAndPersist(UUID itemId) {
        try {
            Item item = repository.findById(itemId).orElse(null);
            if (item == null) return;

            RecipeCostService.CostResult result = recipeCostService.calculate(itemId);
            if (result.complete()) {
                item.setUnitCost(result.totalCostPerUnit());
                repository.save(item);
                log.debug("Đã cập nhật unit_cost={} cho item {}", result.totalCostPerUnit(), itemId);
            }
        } catch (Exception ex) {
            // Không có active recipe hoặc lỗi tính toán — bỏ qua, không chặn approve
            log.warn("Không thể tính cost cho item {}: {}", itemId, ex.getMessage());
        }
    }
}
