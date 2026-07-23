/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.util.UUID;

import java.math.BigDecimal;
import java.util.Optional;

import com.bakery.api.recipe.service.RecipeCostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper tách biệt transaction cho cost recalculation.
 *
 * <p>CHỈ tính toán trong REQUIRES_NEW — KHÔNG ghi vào DB trong transaction này.
 * Lý do: nếu ghi vào cùng row item đang bị outer transaction lock (approve()),
 * sẽ gây ra lock wait deadlock (Connection B chờ Connection A release lock mà
 * Connection A đang suspend chờ Connection B commit — circular wait).
 *
 * <p>Caller (afterApprove) nhận cost và set trực tiếp lên managed entity
 * trong outer transaction → Hibernate tự flush khi outer tx commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemCostHelper {

    private final RecipeCostService recipeCostService;

    /**
     * Tính cost trong transaction độc lập — chỉ đọc, không ghi.
     * Mọi exception đều được swallow, trả về empty nếu không tính được.
     *
     * @return cost per unit nếu tính được và complete=true, empty nếu không
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<BigDecimal> calculateCost(UUID itemId) {
        try {
            RecipeCostService.CostResult result = recipeCostService.calculate(itemId);
            if (result.complete()) {
                log.debug("Tính cost thành công: item={}, cost={}", itemId, result.totalCostPerUnit());
                return Optional.of(result.totalCostPerUnit());
            }
            log.debug("Cost chưa đủ dữ liệu (complete=false) cho item {}", itemId);
            return Optional.empty();
        } catch (Exception ex) {
            // Không có active recipe hoặc lỗi tính toán — bỏ qua, không chặn approve
            log.warn("Không thể tính cost cho item {}: {}", itemId, ex.getMessage());
            return Optional.empty();
        }
    }
}
