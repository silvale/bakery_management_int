package com.bakery.api.modules.sales.controllers;

import com.bakery.api.modules.sales.services.CustomerOrderService;
import com.bakery.api.modules.sales.services.CustomerOrderService.OrderLineRequest;
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
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Customer Orders", description = "Quản lý đơn đặt bánh từ khách hàng")
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    @PostMapping
    @Operation(summary = "Tạo đơn hàng khách")
    public ResponseEntity<Object> createOrder(@RequestBody Map<String, Object> body) {
        try {
            String customerName = body.containsKey("customerName") ? body.get("customerName").toString() : null;
            String customerPhone = body.containsKey("customerPhone") ? body.get("customerPhone").toString() : null;
            LocalDate deliveryDate = LocalDate.parse(body.get("deliveryDate").toString());
            String note = body.containsKey("note") ? body.get("note").toString() : null;
            String createdBy = body.containsKey("createdBy") ? body.get("createdBy").toString() : "system";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawLines = (List<Map<String, Object>>) body.get("lines");
            if (rawLines == null || rawLines.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "lines không được trống"));
            }

            List<OrderLineRequest> lines = rawLines.stream().map(l -> new OrderLineRequest(
                UUID.fromString(l.get("productId").toString()),
                new BigDecimal(l.get("qty").toString()),
                new BigDecimal(l.get("unitPrice").toString()),
                l.containsKey("note") ? l.get("note").toString() : null
            )).collect(Collectors.toList());

            return ResponseEntity.ok(customerOrderService.createOrder(
                customerName, customerPhone, deliveryDate, note, createdBy, lines));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách đơn hàng khách")
    public ResponseEntity<Object> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) String paymentStatus) {
        try {
            return ResponseEntity.ok(customerOrderService.listOrders(status, deliveryDate, paymentStatus));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn hàng kèm dòng hàng và lịch sử thanh toán")
    public ResponseEntity<Object> getOrder(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(customerOrderService.getOrderWithDetails(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận đơn hàng (PENDING → CONFIRMED)")
    public ResponseEntity<Object> confirmOrder(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String confirmedBy = body.containsKey("confirmedBy") ? body.get("confirmedBy").toString() : "admin";
            return ResponseEntity.ok(customerOrderService.confirmOrder(id, confirmedBy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Huỷ đơn hàng")
    public ResponseEntity<Object> cancelOrder(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String cancelledBy = body.containsKey("cancelledBy") ? body.get("cancelledBy").toString() : "admin";
            String reason = body.containsKey("reason") ? body.get("reason").toString() : "";
            return ResponseEntity.ok(customerOrderService.cancelOrder(id, cancelledBy, reason));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Cập nhật trạng thái giao hàng (IN_PRODUCTION | READY | DELIVERED)")
    public ResponseEntity<Object> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String status = body.get("status").toString();
            String updatedBy = body.containsKey("updatedBy") ? body.get("updatedBy").toString() : "system";
            return ResponseEntity.ok(customerOrderService.updateDeliveryStatus(id, status, updatedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/payments")
    @Operation(summary = "Ghi nhận thanh toán cho đơn hàng")
    public ResponseEntity<Object> recordPayment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        try {
            String paymentType = body.containsKey("paymentType") ? body.get("paymentType").toString() : "BANK_TRANSFER";
            BigDecimal amount = new BigDecimal(body.get("amount").toString());
            LocalDate paymentDate = body.containsKey("paymentDate")
                ? LocalDate.parse(body.get("paymentDate").toString()) : LocalDate.now();
            String note = body.containsKey("note") ? body.get("note").toString() : null;
            String recordedBy = body.containsKey("recordedBy") ? body.get("recordedBy").toString() : "system";

            return ResponseEntity.ok(customerOrderService.recordPayment(
                id, paymentType, amount, paymentDate, note, recordedBy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
