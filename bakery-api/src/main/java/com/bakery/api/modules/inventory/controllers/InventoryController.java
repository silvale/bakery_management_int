package com.bakery.api.modules.inventory.controllers;

import com.bakery.api.modules.inventory.services.InventoryAdjustmentService;
import com.bakery.api.modules.inventory.services.InventoryWriteOffService;
import com.bakery.api.modules.inventory.entities.InventoryMovement;
import com.bakery.api.modules.inventory.repositories.InventoryMovementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Quản lý xuất hủy, điều chỉnh kiểm kê và lịch sử tồn kho")
public class InventoryController {

    private final InventoryWriteOffService inventoryWriteOffService;
    private final InventoryAdjustmentService inventoryAdjustmentService;
    private final InventoryMovementRepository inventoryMovementRepository;

    // ── Write-offs ───────────────────────────────────────────────────

    @PostMapping("/write-offs")
    @Operation(summary = "Tạo phiếu xử lý hàng hỏng/hết hạn")
    public ResponseEntity<Object> createWriteOff(@RequestBody Map<String, Object> body) {
        try {
            UUID branchId = UUID.fromString(body.get("branchId").toString());
            String itemType = body.get("itemType").toString();
            UUID ingredientId = body.containsKey("ingredientId") && body.get("ingredientId") != null
                ? UUID.fromString(body.get("ingredientId").toString()) : null;
            UUID productId = body.containsKey("productId") && body.get("productId") != null
                ? UUID.fromString(body.get("productId").toString()) : null;
            UUID lotId = UUID.fromString(body.get("lotId").toString());
            BigDecimal qty = new BigDecimal(body.get("qty").toString());
            String unit = body.containsKey("unit") ? body.get("unit").toString() : "g";
            String reasonType = body.get("reasonType").toString();
            String reasonNote = body.containsKey("reasonNote") ? body.get("reasonNote").toString() : null;
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";

            return ResponseEntity.ok(inventoryWriteOffService.createWriteOff(
                branchId, itemType, ingredientId, productId, lotId, qty, unit, reasonType, reasonNote, createdBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/write-offs")
    @Operation(summary = "Danh sách phiếu xử lý hàng hỏng")
    public ResponseEntity<Object> listWriteOffs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID branchId) {
        try {
            return ResponseEntity.ok(inventoryWriteOffService.listWriteOffs(status, branchId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/write-offs/{id}")
    @Operation(summary = "Chi tiết phiếu xử lý hàng hỏng")
    public ResponseEntity<Object> getWriteOff(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(inventoryWriteOffService.getWriteOff(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/write-offs/{id}/approve")
    @Operation(summary = "Duyệt phiếu xử lý hàng hỏng → trừ tồn kho lô")
    public ResponseEntity<Object> approveWriteOff(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String approvedBy = body.containsKey("approvedBy") ? body.get("approvedBy").toString() : "admin";
            return ResponseEntity.ok(inventoryWriteOffService.approveWriteOff(id, approvedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/write-offs/{id}/reject")
    @Operation(summary = "Từ chối phiếu xử lý hàng hỏng")
    public ResponseEntity<Object> rejectWriteOff(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String rejectedBy = body.containsKey("rejectedBy") ? body.get("rejectedBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(inventoryWriteOffService.rejectWriteOff(id, rejectedBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Adjustments ──────────────────────────────────────────────────

    @PostMapping("/adjustments")
    @Operation(summary = "Tạo phiếu điều chỉnh kiểm kê")
    public ResponseEntity<Object> createAdjustment(@RequestBody Map<String, Object> body) {
        try {
            UUID branchId = UUID.fromString(body.get("branchId").toString());
            String itemType = body.get("itemType").toString();
            UUID ingredientId = body.containsKey("ingredientId") && body.get("ingredientId") != null
                ? UUID.fromString(body.get("ingredientId").toString()) : null;
            UUID productId = body.containsKey("productId") && body.get("productId") != null
                ? UUID.fromString(body.get("productId").toString()) : null;
            UUID lotId = UUID.fromString(body.get("lotId").toString());
            BigDecimal qtyAfter = new BigDecimal(body.get("qtyAfter").toString());
            String reason = body.get("reason").toString();
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";

            return ResponseEntity.ok(inventoryAdjustmentService.createAdjustment(
                branchId, itemType, ingredientId, productId, lotId, qtyAfter, reason, createdBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/adjustments")
    @Operation(summary = "Danh sách phiếu điều chỉnh kiểm kê")
    public ResponseEntity<Object> listAdjustments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID branchId) {
        try {
            return ResponseEntity.ok(inventoryAdjustmentService.listAdjustments(status, branchId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/adjustments/{id}")
    @Operation(summary = "Chi tiết phiếu điều chỉnh kiểm kê")
    public ResponseEntity<Object> getAdjustment(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(inventoryAdjustmentService.getAdjustment(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/adjustments/{id}/approve")
    @Operation(summary = "Duyệt phiếu điều chỉnh kiểm kê → cập nhật tồn kho lô")
    public ResponseEntity<Object> approveAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String approvedBy = body.containsKey("approvedBy") ? body.get("approvedBy").toString() : "admin";
            return ResponseEntity.ok(inventoryAdjustmentService.approveAdjustment(id, approvedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/adjustments/{id}/reject")
    @Operation(summary = "Từ chối phiếu điều chỉnh kiểm kê")
    public ResponseEntity<Object> rejectAdjustment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String rejectedBy = body.containsKey("rejectedBy") ? body.get("rejectedBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(inventoryAdjustmentService.rejectAdjustment(id, rejectedBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Movements ────────────────────────────────────────────────────

    @GetMapping("/movements")
    @Operation(summary = "Lịch sử giao dịch tồn kho")
    public ResponseEntity<Object> listMovements(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID ingredientId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            List<InventoryMovement> movements;

            if (branchId != null && from != null && to != null) {
                OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.UTC);
                OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                movements = inventoryMovementRepository
                    .findAllByBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(branchId, start, end);
            } else if (branchId != null && ingredientId != null) {
                movements = inventoryMovementRepository
                    .findAllByIngredientIdAndBranchIdOrderByCreatedAtDesc(ingredientId, branchId);
            } else if (branchId != null && productId != null) {
                movements = inventoryMovementRepository
                    .findAllByProductIdAndBranchIdOrderByCreatedAtDesc(productId, branchId);
            } else if (branchId != null) {
                movements = inventoryMovementRepository.findAllByBranchIdOrderByCreatedAtDesc(branchId);
            } else {
                movements = inventoryMovementRepository.findAll().stream()
                    .sorted(Comparator.comparing(InventoryMovement::getCreatedAt).reversed())
                    .collect(Collectors.toList());
            }

            List<Map<String, Object>> result = movements.stream().map(mv -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", mv.getId());
                m.put("branchId", mv.getBranch().getId());
                m.put("branchName", mv.getBranch().getName());
                m.put("itemType", mv.getItemType());
                m.put("ingredientId", mv.getIngredient() != null ? mv.getIngredient().getId() : null);
                m.put("ingredientCode", mv.getIngredient() != null ? mv.getIngredient().getCode() : null);
                m.put("productId", mv.getProduct() != null ? mv.getProduct().getId() : null);
                m.put("productCode", mv.getProduct() != null ? mv.getProduct().getCode() : null);
                m.put("lotId", mv.getLotId());
                m.put("transactionType", mv.getTransactionType());
                m.put("referenceType", mv.getReferenceType());
                m.put("qty", mv.getQty());
                m.put("unit", mv.getUnit());
                m.put("sourceType", mv.getSourceType());
                m.put("sourceId", mv.getSourceId());
                m.put("referenceCode", mv.getReferenceCode());
                m.put("note", mv.getNote());
                m.put("createdBy", mv.getCreatedBy());
                m.put("createdAt", mv.getCreatedAt().toString());
                return m;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
