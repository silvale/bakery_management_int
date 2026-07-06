package com.bakery.api.controller;

import com.bakery.api.service.StockTransferService;
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
@RequestMapping("/admin/stock-transfers")
@RequiredArgsConstructor
@Tag(name = "Stock Transfers", description = "Phiếu xuất bánh từ Kho Bếp → Cửa Hàng")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    @Operation(summary = "Tạo phiếu xuất bánh KHO_BEP → SHOP")
    public ResponseEntity<Object> createTransfer(@RequestBody Map<String, Object> body) {
        try {
            UUID productId = UUID.fromString(body.get("productId").toString());
            BigDecimal qtySent = new BigDecimal(body.get("qtySent").toString());
            String unit = body.containsKey("unit") ? body.get("unit").toString() : "PCS";
            LocalDate transferDate = body.containsKey("transferDate")
                ? LocalDate.parse(body.get("transferDate").toString()) : LocalDate.now();
            String note = body.containsKey("note") ? body.get("note").toString() : null;
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";

            return ResponseEntity.ok(stockTransferService.createTransfer(
                productId, qtySent, unit, transferDate, note, createdBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu xuất bánh")
    public ResponseEntity<Object> listTransfers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        try {
            return ResponseEntity.ok(stockTransferService.listTransfers(date, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu xuất bánh")
    public ResponseEntity<Object> getTransfer(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(stockTransferService.getTransfer(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Cửa hàng xác nhận nhận hàng (PENDING → CONFIRMED)")
    public ResponseEntity<Object> confirmTransfer(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            BigDecimal qtyReceived = new BigDecimal(body.get("qtyReceived").toString());
            String confirmedBy = body.containsKey("confirmedBy") ? body.get("confirmedBy").toString() : "shop";
            return ResponseEntity.ok(stockTransferService.confirmTransfer(id, qtyReceived, confirmedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Cửa hàng từ chối nhận hàng (PENDING → REJECTED)")
    public ResponseEntity<Object> rejectTransfer(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String rejectedBy = body.containsKey("rejectedBy") ? body.get("rejectedBy").toString() : "shop";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(stockTransferService.rejectTransfer(id, rejectedBy, reason));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
