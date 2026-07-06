package com.bakery.api.controller;

import com.bakery.api.service.SupplierReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/admin/supplier-returns")
@RequiredArgsConstructor
@Tag(name = "Supplier Returns", description = "Quản lý trả hàng hỏng về nhà cung cấp")
public class SupplierReturnController {

    private final SupplierReturnService supplierReturnService;

    @PostMapping
    @Operation(summary = "Tạo phiếu trả hàng nhà cung cấp")
    public ResponseEntity<Object> createReturn(@RequestBody Map<String, Object> body) {
        try {
            UUID supplierId = UUID.fromString(body.get("supplierId").toString());
            UUID originalPoId = UUID.fromString(body.get("originalPoId").toString());
            UUID writeOffId = body.containsKey("writeOffId") && body.get("writeOffId") != null
                ? UUID.fromString(body.get("writeOffId").toString()) : null;
            LocalDate returnDate = body.containsKey("returnDate")
                ? LocalDate.parse(body.get("returnDate").toString()) : LocalDate.now();
            String reason = body.get("reason").toString();
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";

            return ResponseEntity.ok(supplierReturnService.createReturn(
                supplierId, originalPoId, writeOffId, returnDate, reason, createdBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu trả hàng nhà cung cấp")
    public ResponseEntity<Object> listReturns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId) {
        try {
            return ResponseEntity.ok(supplierReturnService.listReturns(status, supplierId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu trả hàng nhà cung cấp")
    public ResponseEntity<Object> getReturn(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(supplierReturnService.getReturn(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/sent")
    @Operation(summary = "Đánh dấu đã gửi hàng về nhà cung cấp (→ SENT_TO_SUPPLIER)")
    public ResponseEntity<Object> markSent(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String updatedBy = (body != null && body.containsKey("updatedBy"))
                ? body.get("updatedBy").toString() : "system";
            return ResponseEntity.ok(supplierReturnService.markSentToSupplier(id, updatedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/replacement")
    @Operation(summary = "Gắn PO bù hàng thay thế từ nhà cung cấp (→ REPLACEMENT_RECEIVED)")
    public ResponseEntity<Object> linkReplacement(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            UUID replacementPoId = UUID.fromString(body.get("replacementPoId").toString());
            String updatedBy = body.containsKey("updatedBy") ? body.get("updatedBy").toString() : "system";
            return ResponseEntity.ok(supplierReturnService.linkReplacementPo(id, replacementPoId, updatedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/write-off")
    @Operation(summary = "Đánh dấu đã xử lý huỷ (→ WRITTEN_OFF)")
    public ResponseEntity<Object> markWrittenOff(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String updatedBy = (body != null && body.containsKey("updatedBy"))
                ? body.get("updatedBy").toString() : "system";
            return ResponseEntity.ok(supplierReturnService.markWrittenOff(id, updatedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
