/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Danh sách mặt hàng dưới ngưỡng tồn kho tối thiểu.
 *
 * <p>GET /api/v1/items/low-stock
 * → [ { itemId, code, name, unit, itemType, minStockQuantity, currentStock, shortage } ]
 */
@RestController
@RequiredArgsConstructor
public class LowStockController {

    private final EntityManager em;

    @GetMapping("/api/v1/items/low-stock")
    public ResponseEntity<List<Map<String, Object>>> getLowStockItems() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT
                    i.id::text                          AS item_id,
                    i.code,
                    i.name,
                    i.unit,
                    i.item_type,
                    i.min_stock_quantity,
                    COALESCE(SUM(sl.qty_remaining), 0)  AS current_stock,
                    i.min_stock_quantity - COALESCE(SUM(sl.qty_remaining), 0) AS shortage
                FROM item i
                LEFT JOIN stock_lot sl ON sl.item_id = i.id
                WHERE i.min_stock_quantity IS NOT NULL
                  AND i.min_stock_quantity > 0
                  AND i.item_type IN ('INGREDIENT', 'SEMI_PRODUCT')
                  AND (i.status IS NULL OR i.status != 'INACTIVE')
                GROUP BY i.id, i.code, i.name, i.unit, i.item_type, i.min_stock_quantity
                HAVING COALESCE(SUM(sl.qty_remaining), 0) < i.min_stock_quantity
                ORDER BY shortage DESC, i.name
                """).getResultList();

        List<Map<String, Object>> result = rows.stream().map(r -> Map.<String, Object>of(
                "itemId",           r[0],
                "code",             r[1],
                "name",             r[2],
                "unit",             r[3],
                "itemType",         r[4],
                "minStockQuantity", r[5],
                "currentStock",     r[6],
                "shortage",         r[7]
        )).toList();

        return ResponseEntity.ok(result);
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
