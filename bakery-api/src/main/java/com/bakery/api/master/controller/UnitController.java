/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.bakery.api.master.entity.Unit;
import com.bakery.api.master.entity.UnitConversion;
import com.bakery.api.master.entity.UnitConversionId;
import com.bakery.api.master.repository.UnitConversionRepository;
import com.bakery.api.master.repository.UnitRepository;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý đơn vị tính.
 *
 * GET    /api/v1/units                          — Danh sách tất cả đơn vị
 * POST   /api/v1/units                          — Thêm đơn vị mới
 * PUT    /api/v1/units/{code}                   — Cập nhật tên/ghi chú
 * DELETE /api/v1/units/{code}                   — Xóa đơn vị
 *
 * GET    /api/v1/units/conversions              — Danh sách tỉ lệ quy đổi
 * POST   /api/v1/units/conversions              — Thêm tỉ lệ mới
 * PUT    /api/v1/units/conversions/{from}/{to}  — Cập nhật hệ số/ghi chú
 * DELETE /api/v1/units/conversions/{from}/{to}  — Xóa tỉ lệ
 */
@RestController
@RequestMapping("/api/v1/units")
@RequiredArgsConstructor
@RequirePermission(screen = "UNITS", action = "VIEW")
public class UnitController {

    private final UnitRepository unitRepository;
    private final UnitConversionRepository conversionRepository;

    // ── Unit CRUD ─────────────────────────────────────────────────────────────

    @GetMapping
    public List<Map<String, Object>> listUnits() {
        return unitRepository.findAll().stream()
                .sorted((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()))
                .map(u -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("code", u.getCode());
                    m.put("name", u.getName());
                    m.put("note", u.getNote() != null ? u.getNote() : "");
                    return m;
                })
                .toList();
    }

    @PostMapping
    @RequirePermission(screen = "UNITS", action = "CREATE")
    public ResponseEntity<Map<String, Object>> createUnit(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "").trim().toUpperCase();
        String name = body.getOrDefault("name", "").trim();
        if (code.isEmpty() || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code và name là bắt buộc"));
        }
        if (unitRepository.existsById(code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Đơn vị " + code + " đã tồn tại"));
        }
        Unit unit = new Unit();
        unit.setCode(code);
        unit.setName(name);
        unit.setNote(body.get("note"));
        unitRepository.save(unit);
        return ResponseEntity.ok(Map.of("code", unit.getCode(), "name", unit.getName()));
    }

    @PutMapping("/{code}")
    @RequirePermission(screen = "UNITS", action = "UPDATE")
    public ResponseEntity<Map<String, Object>> updateUnit(
            @PathVariable String code,
            @RequestBody Map<String, String> body) {
        return unitRepository.findById(code.toUpperCase())
                .map(unit -> {
                    if (body.containsKey("name") && !body.get("name").isBlank()) {
                        unit.setName(body.get("name").trim());
                    }
                    unit.setNote(body.get("note"));
                    unitRepository.save(unit);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "code", unit.getCode(), "name", unit.getName()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{code}")
    @RequirePermission(screen = "UNITS", action = "DELETE")
    public ResponseEntity<Void> deleteUnit(@PathVariable String code) {
        String upper = code.toUpperCase();
        if (!unitRepository.existsById(upper)) {
            return ResponseEntity.notFound().build();
        }
        unitRepository.deleteById(upper);
        return ResponseEntity.noContent().build();
    }

    // ── Conversion CRUD ───────────────────────────────────────────────────────

    @GetMapping("/conversions")
    public List<Map<String, Object>> listConversions() {
        return conversionRepository.findAll().stream()
                .sorted((a, b) -> a.getFromUnit().compareToIgnoreCase(b.getFromUnit()))
                .map(c -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("fromUnit", c.getFromUnit());
                    m.put("toUnit", c.getToUnit());
                    m.put("factor", c.getFactor());
                    m.put("note", c.getNote() != null ? c.getNote() : "");
                    // Ví dụ: "1 G = 0.001 KG"
                    m.put("example", "1 " + c.getFromUnit() + " = " + c.getFactor().toPlainString() + " " + c.getToUnit());
                    return m;
                })
                .toList();
    }

    @PostMapping("/conversions")
    @RequirePermission(screen = "UNITS", action = "CREATE")
    public ResponseEntity<Map<String, Object>> createConversion(@RequestBody Map<String, Object> body) {
        String fromUnit = ((String) body.getOrDefault("fromUnit", "")).trim().toUpperCase();
        String toUnit   = ((String) body.getOrDefault("toUnit",   "")).trim().toUpperCase();
        Object factorRaw = body.get("factor");
        if (fromUnit.isEmpty() || toUnit.isEmpty() || factorRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "fromUnit, toUnit và factor là bắt buộc"));
        }
        BigDecimal factor;
        try {
            factor = new BigDecimal(factorRaw.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "factor không hợp lệ"));
        }
        UnitConversionId id = new UnitConversionId(fromUnit, toUnit);
        if (conversionRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tỉ lệ " + fromUnit + " → " + toUnit + " đã tồn tại"));
        }
        UnitConversion conv = new UnitConversion();
        conv.setFromUnit(fromUnit);
        conv.setToUnit(toUnit);
        conv.setFactor(factor);
        conv.setNote(body.containsKey("note") ? (String) body.get("note") : null);
        conversionRepository.save(conv);
        return ResponseEntity.ok(Map.of("fromUnit", fromUnit, "toUnit", toUnit, "factor", factor));
    }

    @PutMapping("/conversions/{fromUnit}/{toUnit}")
    @RequirePermission(screen = "UNITS", action = "UPDATE")
    public ResponseEntity<Map<String, Object>> updateConversion(
            @PathVariable String fromUnit,
            @PathVariable String toUnit,
            @RequestBody Map<String, Object> body) {
        UnitConversionId id = new UnitConversionId(fromUnit.toUpperCase(), toUnit.toUpperCase());
        return conversionRepository.findById(id)
                .map(conv -> {
                    if (body.containsKey("factor") && body.get("factor") != null) {
                        conv.setFactor(new BigDecimal(body.get("factor").toString()));
                    }
                    conv.setNote(body.containsKey("note") ? (String) body.get("note") : conv.getNote());
                    conversionRepository.save(conv);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "fromUnit", conv.getFromUnit(),
                            "toUnit", conv.getToUnit(),
                            "factor", conv.getFactor()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/conversions/{fromUnit}/{toUnit}")
    @RequirePermission(screen = "UNITS", action = "DELETE")
    public ResponseEntity<Void> deleteConversion(
            @PathVariable String fromUnit,
            @PathVariable String toUnit) {
        UnitConversionId id = new UnitConversionId(fromUnit.toUpperCase(), toUnit.toUpperCase());
        if (!conversionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        conversionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
