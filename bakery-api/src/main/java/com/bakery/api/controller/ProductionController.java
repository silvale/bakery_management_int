package com.bakery.api.controller;

import com.bakery.api.service.ProductionPlanService;
import com.bakery.api.service.ProductionPlannerService;
import com.bakery.api.service.ProductionRequestService;
import com.bakery.api.service.CustomerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/production")
@RequiredArgsConstructor
@Tag(name = "Production", description = "Quản lý kế hoạch sản xuất và lệnh sản xuất")
public class ProductionController {

    private final ProductionPlanService     productionPlanService;
    private final ProductionPlannerService  productionPlannerService;
    private final ProductionRequestService  productionRequestService;
    private final CustomerOrderService      customerOrderService;

    // ── Production Plans ─────────────────────────────────────────────

    @GetMapping("/plans")
    @Operation(summary = "Danh sách kế hoạch sản xuất")
    public ResponseEntity<Object> listPlans(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        try {
            return ResponseEntity.ok(productionPlanService.listPlans(status, dateFrom, dateTo));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/plans/{id}")
    @Operation(summary = "Chi tiết kế hoạch sản xuất kèm danh sách dòng")
    public ResponseEntity<Object> getPlan(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(productionPlanService.getPlanWithLines(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/plans/{id}/lines/{lineId}")
    @Operation(summary = "Điều chỉnh số lượng một dòng trong kế hoạch")
    public ResponseEntity<Object> adjustPlanLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestBody Map<String, Object> body) {
        try {
            BigDecimal qtyAdjusted = new BigDecimal(body.get("qtyAdjusted").toString());
            String note = body.containsKey("note") ? body.get("note").toString() : null;
            return ResponseEntity.ok(productionPlanService.adjustPlanLine(id, lineId, qtyAdjusted, note));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/plans/{id}/approve")
    @Operation(summary = "Duyệt kế hoạch sản xuất → tạo ProductionRequest cho từng dòng")
    public ResponseEntity<Object> approvePlan(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String approvedBy = body.containsKey("approvedBy") ? body.get("approvedBy").toString() : "admin";
            return ResponseEntity.ok(productionPlanService.approvePlan(id, approvedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/plans/{id}/reject")
    @Operation(summary = "Từ chối kế hoạch sản xuất")
    public ResponseEntity<Object> rejectPlan(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String rejectedBy = body.containsKey("rejectedBy") ? body.get("rejectedBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(productionPlanService.rejectPlan(id, rejectedBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Production Requests ─────────────────────────────────────────

    @GetMapping("/requests")
    @Operation(summary = "Danh sách lệnh sản xuất")
    public ResponseEntity<Object> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            return ResponseEntity.ok(productionRequestService.listRequests(status, requestType, date));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "Chi tiết lệnh sản xuất")
    public ResponseEntity<Object> getRequest(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(productionRequestService.getRequest(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests")
    @Operation(summary = "Tạo lệnh sản xuất ad-hoc (URGENT hoặc CUSTOMER_ORDER)")
    public ResponseEntity<Object> createAdHocRequest(@RequestBody Map<String, Object> body) {
        try {
            UUID productId = UUID.fromString(body.get("productId").toString());
            UUID recipeId = body.containsKey("recipeId") && body.get("recipeId") != null
                ? UUID.fromString(body.get("recipeId").toString()) : null;
            String requestType = body.containsKey("requestType") ? body.get("requestType").toString() : "URGENT";
            BigDecimal qtyPlanned = new BigDecimal(body.get("qtyPlanned").toString());
            UUID customerOrderId = body.containsKey("customerOrderId") && body.get("customerOrderId") != null
                ? UUID.fromString(body.get("customerOrderId").toString()) : null;
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";
            String note = body.containsKey("note") ? body.get("note").toString() : null;

            return ResponseEntity.ok(productionRequestService.createAdHocRequest(
                productId, recipeId, requestType, qtyPlanned, customerOrderId, createdBy, note));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Duyệt lệnh sản xuất")
    public ResponseEntity<Object> approveRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String approvedBy = body.containsKey("approvedBy") ? body.get("approvedBy").toString() : "admin";
            return ResponseEntity.ok(productionRequestService.approveRequest(id, approvedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Từ chối lệnh sản xuất")
    public ResponseEntity<Object> rejectRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String rejectedBy = body.containsKey("rejectedBy") ? body.get("rejectedBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(productionRequestService.rejectRequest(id, rejectedBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/start")
    @Operation(summary = "Bắt đầu sản xuất (APPROVED → IN_PRODUCTION)")
    public ResponseEntity<Object> startProduction(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String startedBy = body.containsKey("startedBy") ? body.get("startedBy").toString() : "system";
            return ResponseEntity.ok(productionRequestService.startProduction(id, startedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/complete")
    @Operation(summary = "Hoàn thành sản xuất (IN_PRODUCTION → COMPLETED)")
    public ResponseEntity<Object> completeRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            BigDecimal qtyActual = new BigDecimal(body.get("qtyActual").toString());
            String varianceReason = body.containsKey("varianceReason") ? body.get("varianceReason").toString() : null;
            String completedBy = body.containsKey("completedBy") ? body.get("completedBy").toString() : "system";
            return ResponseEntity.ok(productionRequestService.completeRequest(id, qtyActual, varianceReason, completedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/requests/{id}/cancel")
    @Operation(summary = "Huỷ lệnh sản xuất")
    public ResponseEntity<Object> cancelRequest(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String cancelledBy = body.containsKey("cancelledBy") ? body.get("cancelledBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(productionRequestService.cancelRequest(id, cancelledBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Production Planning Engine ────────────────────────────────────

    /**
     * Tạo kế hoạch sản xuất cho ngày targetDate.
     * Chạy đủ 3 pattern: GROUP_SUBTRACT + LAN_MAM + LAN_XUAT.
     * Idempotent — gọi lại sẽ override plan cũ.
     *
     * Body (optional):
     *   { "cotBap": 2, "createdBy": "admin" }
     */
    @PostMapping("/plan/generate")
    @Operation(summary = "Tạo kế hoạch sản xuất (chạy 3 pattern)")
    public ResponseEntity<Object> generatePlan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String createdBy = body != null && body.containsKey("createdBy")
                    ? body.get("createdBy").toString() : "system";
            var plan = productionPlannerService.generateDailyPlan(targetDate, createdBy);
            return ResponseEntity.ok(Map.of(
                    "planId", plan.getId(),
                    "planDate", plan.getPlanDate().toString(),
                    "status", plan.getStatus(),
                    "totalLines", plan.getLines().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Sheet Cake Order Workflow ─────────────────────────────────────

    /**
     * Danh sách đơn SHEET_CAKE CONFIRMED cần bếp review trước giờ chốt sổ.
     */
    @GetMapping("/sheet-cake/pending")
    @Operation(summary = "Đơn bánh kem thiết kế cần review (CONFIRMED, chưa IN_PRODUCTION)")
    public ResponseEntity<Object> listSheetCakePending(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate) {
        try {
            return ResponseEntity.ok(customerOrderService.listSheetCakePendingProduction(deliveryDate));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Thêm topping / NL add-on đặc thù cho 1 line của đơn SHEET_CAKE.
     *
     * Body: { "addonType": "INGREDIENT", "ingredientId": "uuid", "qty": 50, "unit": "g", "note": "dâu tây tươi" }
     */
    @PostMapping("/sheet-cake/lines/{lineId}/addons")
    @Operation(summary = "Thêm topping/NL add-on đặc thù cho đơn bánh kem")
    public ResponseEntity<Object> addAddon(
            @PathVariable UUID lineId,
            @RequestBody Map<String, Object> body) {
        try {
            String addonType = body.getOrDefault("addonType", "INGREDIENT").toString();
            UUID ingredientId = body.containsKey("ingredientId") && body.get("ingredientId") != null
                    ? UUID.fromString(body.get("ingredientId").toString()) : null;
            UUID productId = body.containsKey("productId") && body.get("productId") != null
                    ? UUID.fromString(body.get("productId").toString()) : null;
            BigDecimal qty = new BigDecimal(body.get("qty").toString());
            String unit = body.getOrDefault("unit", "g").toString();
            String note = body.containsKey("note") ? body.get("note").toString() : null;
            String createdBy = body.getOrDefault("createdBy", "system").toString();

            return ResponseEntity.ok(customerOrderService.addAddon(
                    lineId, addonType, ingredientId, productId, qty, unit, note, createdBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lock đơn SHEET_CAKE vào sản xuất: CONFIRMED → IN_PRODUCTION.
     * Sau bước này addon list bị lock, FifoEngine trừ kho NL.
     */
    @PostMapping("/sheet-cake/orders/{id}/lock-production")
    @Operation(summary = "Lock đơn bánh kem vào sản xuất (CONFIRMED → IN_PRODUCTION)")
    public ResponseEntity<Object> lockForProduction(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String lockedBy = body.containsKey("lockedBy") ? body.get("lockedBy").toString() : "admin";
            return ResponseEntity.ok(customerOrderService.lockForProduction(id, lockedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
