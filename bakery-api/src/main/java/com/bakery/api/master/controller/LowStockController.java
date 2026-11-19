/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Danh sách mặt hàng dưới ngưỡng tồn kho tối thiểu.
 *
 * <p>GET /api/v1/items/low-stock
 * → [ { itemId, code, name, unit, itemType, minStockQuantity, currentStock, shortage, suggestedQty } ]
 *
 * <p>{@code ?includeNear=true} (09/BE-1): thêm mặt hàng đủ ngưỡng nhưng còn sát (tồn ≤ 125% ngưỡng);
 * các dòng đó có {@code shortage <= 0}. Không tham số = như cũ (tồn &lt; ngưỡng).
 */
@RestController
@RequiredArgsConstructor
@com.bakery.framework.security.RequirePermission(screen = "LOW_STOCK", action = "VIEW")
public class LowStockController {

    private final EntityManager em;

    @GetMapping("/api/v1/items/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockItems(
            @RequestParam(defaultValue = "false") boolean includeNear) {
        // Chỉ chọn giữa hai điều kiện cố định, không nối giá trị người dùng vào SQL
        String having = includeNear
                ? "COALESCE(SUM(sl.qty_remaining), 0) <= i.min_stock_quantity * 1.25"
                : "COALESCE(SUM(sl.qty_remaining), 0) < i.min_stock_quantity";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT
                    i.id::text                          AS item_id,
                    i.code,
                    i.name,
                    i.unit,
                    i.item_type,
                    i.min_stock_quantity,
                    i.restock_quantity,
                    COALESCE(SUM(sl.qty_remaining), 0)  AS current_stock,
                    i.min_stock_quantity - COALESCE(SUM(sl.qty_remaining), 0) AS shortage
                FROM item i
                LEFT JOIN stock_lot sl ON sl.item_id = i.id
                WHERE i.min_stock_quantity IS NOT NULL
                  AND i.min_stock_quantity > 0
                  AND i.item_type IN ('INGREDIENT', 'SEMI_PRODUCT')
                  AND (i.status IS NULL OR i.status != 'INACTIVE')
                GROUP BY i.id, i.code, i.name, i.unit, i.item_type, i.min_stock_quantity, i.restock_quantity
                HAVING %s
                ORDER BY shortage DESC, i.name
                """.formatted(having)).getResultList();

        List<Map<String, Object>> result = rows.stream().map(r -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("itemId",           r[0]);
            m.put("code",             r[1]);
            m.put("name",             r[2]);
            m.put("unit",             r[3]);
            m.put("itemType",         r[4]);
            m.put("minStockQuantity", r[5]);
            m.put("restockQuantity",  r[6]);
            m.put("currentStock",     r[7]);
            m.put("shortage",         r[8]);
            BigDecimal shortage = (BigDecimal) r[8];
            // 09/BE-2: dòng gần ngưỡng (includeNear) chưa cần nhập → gợi ý 0
            BigDecimal suggested = shortage.signum() <= 0 ? BigDecimal.ZERO
                    : suggestedQty((BigDecimal) r[6], (BigDecimal) r[7], shortage);
            m.put("suggestedQty",     suggested);
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }

    /**
     * Số lượng nên nhập: có mức nhập thì nhập đủ lên mức đó (restock − tồn), không thì bù đủ ngưỡng (shortage).
     * Mức nhập cấu hình thấp hơn tồn hiện tại (kết quả ≤ 0) → quay về shortage.
     */
    static BigDecimal suggestedQty(BigDecimal restock, BigDecimal current, BigDecimal shortage) {
        if (restock != null && restock.signum() > 0) {
            BigDecimal q = restock.subtract(current);
            if (q.signum() > 0) return q;
        }
        return shortage;
    }

    /** Badge count — số mặt hàng đang dưới ngưỡng. */
    @GetMapping("/api/v1/items/low-stock/count")
    public ResponseEntity<Map<String, Object>> getLowStockCount() {
        Number count = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM (
                    SELECT i.id
                    FROM item i
                    LEFT JOIN stock_lot sl ON sl.item_id = i.id
                    WHERE i.min_stock_quantity IS NOT NULL
                      AND i.min_stock_quantity > 0
                      AND i.item_type IN ('INGREDIENT', 'SEMI_PRODUCT')
                      AND (i.status IS NULL OR i.status != 'INACTIVE')
                    GROUP BY i.id, i.min_stock_quantity
                    HAVING COALESCE(SUM(sl.qty_remaining), 0) < i.min_stock_quantity
                ) sub
                """).getSingleResult();

        return ResponseEntity.ok(Map.of("count", count.intValue()));
    }
}
