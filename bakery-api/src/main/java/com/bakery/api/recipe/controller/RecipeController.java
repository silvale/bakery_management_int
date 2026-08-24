/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.controller;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.master.service.ItemService;
import com.bakery.api.master.repository.ProductRepository;
import com.bakery.api.master.repository.SemiProductRepository;
import com.bakery.api.master.entity.Item;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import com.bakery.api.recipe.dto.RecipeRequest;
import com.bakery.api.recipe.dto.RecipeResponse;
import com.bakery.api.recipe.service.RecipeCostService;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import com.bakery.api.recipe.service.RecipeExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản lý công thức sản phẩm (Recipe).
 *
 * <p>Framework endpoints (từ BakeryAdminResource):
 *   GET    /api/v1/recipes               → list + filter
 *   GET    /api/v1/recipes/all           → list không phân trang
 *   GET    /api/v1/recipes/{id}          → chi tiết
 *   POST   /api/v1/recipes               → tạo mới
 *   PUT    /api/v1/recipes/{id}          → cập nhật
 *   DELETE /api/v1/recipes/{id}          → xóa
 *   POST   /api/v1/recipes/{id}/approve  → duyệt
 *   POST   /api/v1/recipes/{id}/reject   → từ chối
 *
 * <p>Custom endpoints:
 *   POST   /api/v1/recipes/{id}/activate          → kích hoạt
 *   POST   /api/v1/recipes/{id}/clone             → nhân bản
 *   GET    /api/v1/recipes/by-product/{productId} → theo sản phẩm
 *   GET    /api/v1/recipes/by-semi/{semiProductId}→ theo bán thành phẩm
 *   GET    /api/v1/recipes/cost/{itemId}          → tính giá cost (preview, không lưu)
 *   POST   /api/v1/recipes/cost/{itemId}/apply    → tính + lưu vào item.unit_cost
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class RecipeController extends BakeryAdminResource<RecipeRequest, RecipeResponse> {

    private final RecipeService service;
    private final RecipeCostService recipeCostService;
    private final ItemService itemService;
    private final RecipeExportService recipeExportService;
    private final ProductRepository productRepository;
    private final SemiProductRepository semiProductRepository;

    @Override
    protected BakeryAdminService<RecipeRequest, RecipeResponse> getService() {
        return service;
    }

    @PostMapping("/{id}/activate")
    public RecipeResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/clone")
    public RecipeResponse clone(@PathVariable UUID id) {
        return service.clone(id);
    }

    @GetMapping("/by-product/{productId}")
    public List<RecipeResponse> byProduct(@PathVariable UUID productId) {
        return service.findByProduct(productId);
    }

    @GetMapping("/by-semi/{semiProductId}")
    public List<RecipeResponse> bySemiProduct(@PathVariable UUID semiProductId) {
        return service.findBySemiProduct(semiProductId);
    }

    /**
     * Tính giá cost cho 1 SP / BTP — chỉ đọc, không lưu vào DB.
     * Trả về breakdown chi tiết từng nguyên liệu + nguồn giá (CATALOG/STOCK_LOT_AVG/MISSING).
     */
    @GetMapping("/cost/{itemId}")
    public RecipeCostService.CostResult calculateCost(@PathVariable UUID itemId) {
        return recipeCostService.calculate(itemId);
    }

    /**
     * Tính giá cost + lưu vào item.unit_cost.
     * Chỉ lưu khi complete=true (tất cả NL đều tìm được giá).
     * Luôn trả về CostResult để UI biết kết quả.
     */
    @PostMapping("/cost/{itemId}/apply")
    public RecipeCostService.CostResult applyCost(@PathVariable UUID itemId) {
        RecipeCostService.CostResult result = recipeCostService.calculate(itemId);
        if (result.complete()) {
            itemService.saveUnitCost(itemId, result.totalCostPerUnit());
        }
        return result;
    }
    /**
     * Trả danh sách recipe line trong active recipe có đơn vị UNIT_MISMATCH:
     * - lineUnit ≠ ingredient.unit (khác đơn vị)
     * - KHÔNG có bản ghi unit_conversion để quy đổi
     *
     * Dùng để chẩn đoán và sửa công thức / bổ sung unit_conversion.
     */
    @GetMapping("/unit-issues")
    public List<Map<String, Object>> unitIssues() {
        List<Object[]> rows = service.findUnitMismatchIssues();
        return rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode",      r[0]);
            m.put("productName",      r[1]);
            m.put("productType",      r[2]);
            m.put("ingredientCode",   r[3]);
            m.put("ingredientName",   r[4]);
            m.put("ingredientUnit",   r[5]);
            m.put("recipeUnit",       r[6]);
            return m;
        }).toList();
    }


    /**
     * Xuất công thức của nhiều sản phẩm / bán thành phẩm ra Excel.
     * POST /api/v1/recipes/export
     * Body: ["uuid1", "uuid2", ...]
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportRecipes(@RequestBody List<UUID> itemIds) {
        byte[] data = recipeExportService.exportRecipes(itemIds);
        String filename = "cong-thuc-" + System.currentTimeMillis() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    /**
     * Tính lại giá vốn cho TOÀN BỘ Sản Phẩm + Bán Thành Phẩm có active recipe.
     * POST /api/v1/recipes/cost/apply-all
     * Trả về: { updated, skipped, noRecipe, errors[] }
     */
    @PostMapping("/cost/apply-all")
    public Map<String, Object> applyAllCosts() {
        List<Item> all = Stream.concat(
                productRepository.findAll().stream(),
                semiProductRepository.findAll().stream()
        ).toList();

        int updated = 0, skipped = 0, noRecipe = 0;
        List<String> errors = new ArrayList<>();

        for (Item item : all) {
            try {
                RecipeCostService.CostResult result = recipeCostService.calculate(item.getId());
                if (result.complete()) {
                    itemService.saveUnitCost(item.getId(), result.totalCostPerUnit());
                    updated++;
                } else {
                    skipped++;
                    errors.add(item.getCode() + ": thiếu giá " +
                            result.breakdown().stream()
                                    .filter(l -> "MISSING".equals(l.priceSource()))
                                    .map(RecipeCostService.LineCost::itemCode)
                                    .limit(3)
                                    .reduce((a, b) -> a + ", " + b).orElse(""));
                }
            } catch (Exception e) {
                noRecipe++;
                // Không log item không có active recipe (bình thường)
                String msg = e.getMessage();
                if (msg != null && !msg.contains("chưa có active recipe")) {
                    errors.add(item.getCode() + ": " + msg);
                }
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("updated", updated);
        res.put("skipped", skipped);
        res.put("noRecipe", noRecipe);
        res.put("errors", errors);
        return res;
    }

    /**
     * Tự động fill yieldQuantity = tổng KG nguyên liệu cho tất cả active recipe
     * chưa có yieldQuantity (null hoặc <= 0).
     * POST /api/v1/recipes/yield/auto-fill
     * Trả về: { total, filled, alreadySet, noKgIngredients, details[] }
     */
    @PostMapping("/yield/auto-fill")
    public java.util.Map<String, Object> autoFillYield() {
        return service.autoFillYieldQuantity();
    }

}
