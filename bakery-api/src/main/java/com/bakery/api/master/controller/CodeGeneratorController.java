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

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sinh mã tự động (code) có check trùng trong DB.
 *
 * <p>GET /api/v1/codes/generate?prefix=NCC&entity=supplier
 * → { "code": "NCC003" }
 *
 * <p>Format: {PREFIX}{số 3 chữ số, padding 0} — VD: SP001, NCC042, NL123
 */
@RestController
@RequiredArgsConstructor
public class CodeGeneratorController {

    private final EntityManager em;

    /** Whitelist entity → table name (chặn SQL injection). */
    private static final Map<String, String> ENTITY_TABLE = Map.of(
            "item",        "item",
            "supplier",    "supplier",
            "itemGroup",   "item_group",
            "prodGroup",   "production_group",
            "unit",        "unit"
    );

    @GetMapping("/api/v1/codes/generate")
    public ResponseEntity<?> generate(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "item") String entity) {

        String table = ENTITY_TABLE.get(entity);
        if (table == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "entity không hợp lệ: " + entity));
        }

        String prefixUpper = prefix.trim().toUpperCase();
        if (prefixUpper.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "prefix không được để trống"));
        }

        // Lấy tất cả code theo prefix (case-insensitive) từ bảng tương ứng
        @SuppressWarnings("unchecked")
        List<String> codes = em.createNativeQuery(
                "SELECT code FROM " + table + " WHERE UPPER(code) LIKE :pattern"
        ).setParameter("pattern", prefixUpper + "%").getResultList();

        // Tìm số lớn nhất trong các code có dạng PREFIX + chỉ số
        int maxNum = 0;
        for (String code : codes) {
            if (code == null) continue;
            String suffix = code.substring(Math.min(prefixUpper.length(), code.length()));
            if (suffix.matches("\\d+")) {
                maxNum = Math.max(maxNum, Integer.parseInt(suffix));
            }
        }

        // Sinh code tiếp theo, pad 3 chữ số (001..999), nếu > 999 thì không pad
        int nextNum = maxNum + 1;
        String nextCode = prefixUpper + String.format("%03d", nextNum);

        // Double-check trùng (edge case)
        final String finalCode = codes.stream()
                .map(String::toUpperCase)
                .anyMatch(c -> c.equals(nextCode))
                ? prefixUpper + String.format("%03d", nextNum + 1)
                : nextCode;

        return ResponseEntity.ok(Map.of("code", finalCode));
    }
}
