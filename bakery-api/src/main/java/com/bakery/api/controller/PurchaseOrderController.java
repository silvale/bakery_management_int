package com.bakery.api.controller;

import com.bakery.api.service.PurchaseOrderService;
import com.bakery.api.service.PurchaseOrderService.*;
import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/purchase")
@RequiredArgsConstructor
@Tag(name = "Purchase", description = "Quản lý nhập hàng, nhà cung cấp, tồn kho")
public class PurchaseOrderController {

    private final PurchaseOrderService    purchaseOrderService;
    private final SupplierRepository      supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockLotRepository      stockLotRepository;
    private final IngredientRepository    ingredientRepository;

    // ── Supplier ──────────────────────────────────────────────

    @GetMapping("/suppliers")
    @Operation(summary = "Danh sách nhà cung cấp")
    public List<Map<String, Object>> getSuppliers() {
        return supplierRepository.findAllByIsActiveTrue().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",      s.getId());
                    m.put("code",    s.getCode());
                    m.put("name",    s.getName());
                    m.put("address", s.getAddress() != null ? s.getAddress() : "");
                    m.put("phone",   s.getPhone()   != null ? s.getPhone()   : "");
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/suppliers/{code}")
    @Operation(summary = "Lấy thông tin nhà cung cấp theo code")
    public ResponseEntity<Map<String, Object>> getSupplierByCode(@PathVariable String code) {
        return supplierRepository.findByCode(code)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",      s.getId());
                    m.put("code",    s.getCode());
                    m.put("name",    s.getName());
                    m.put("address", s.getAddress() != null ? s.getAddress() : "");
                    m.put("phone",   s.getPhone()   != null ? s.getPhone()   : "");
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/suppliers")
    @Operation(summary = "Thêm nhà cung cấp mới")
    public ResponseEntity<Map<String, Object>> createSupplier(
            @RequestBody Map<String, String> body) {

        Supplier supplier = Supplier.builder()
                .code(body.get("code"))
                .name(body.get("name"))
                .address(body.get("address"))
                .phone(body.get("phone"))
                .build();
        supplierRepository.save(supplier);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",   supplier.getId());
        resp.put("code", supplier.getCode());
        resp.put("name", supplier.getName());
        return ResponseEntity.ok(resp);
    }

    // ── Purchase Order ────────────────────────────────────────

    @PostMapping("/orders/receive")
    @Operation(summary = "Tạo đơn nhập hàng + nhận hàng ngay trong 1 lần")
    public ResponseEntity<Map<String, Object>> createAndReceive(
            @RequestBody Map<String, Object> body) {

        List<?> rawIngredients = (List<?>) body.get("ingredients");
        List<ReceiveLineRequest> lines = rawIngredients.stream().map(obj -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) obj;
            String code = item.containsKey("ingredientCode")
                          ? item.get("ingredientCode").toString() : null;
            UUID   id   = (code == null || code.isBlank()) && item.containsKey("ingredientId")
                          ? UUID.fromString(item.get("ingredientId").toString()) : null;
            return new ReceiveLineRequest(
                    id,
                    code,
                    item.get("purchaseUnit").toString(),
                    new BigDecimal(item.get("qtyOrdered").toString()),
                    new BigDecimal(item.get("qtyReceived").toString()),
                    new BigDecimal(item.get("unitPrice").toString()),
                    item.get("expiryDate") != null && !item.get("expiryDate").toString().equals("null")
                            ? java.time.LocalDate.parse(item.get("expiryDate").toString())
                            : null,
                    item.getOrDefault("note", "").toString()
            );
        }).collect(Collectors.toList());

        PurchaseOrder order = purchaseOrderService.createAndReceive(
                new PurchaseOrderService.CreateAndReceiveRequest(
                        UUID.fromString(body.get("supplierId").toString()),
                        java.time.LocalDate.parse(body.get("orderDate").toString()),
                        body.getOrDefault("note", "").toString(),
                        lines
                )
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",          order.getId());
        resp.put("code",        order.getCode());
        resp.put("totalAmount", order.getTotalAmount());
        resp.put("status",      "RECEIVED");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders")
    @Operation(summary = "Tạo đơn nhập hàng (chưa nhận — dùng khi đặt hàng trước)")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> body) {

        PurchaseOrder order = purchaseOrderService.createOrder(new CreateOrderRequest(
                UUID.fromString(body.get("supplierId").toString()),
                java.time.LocalDate.parse(body.get("orderDate").toString()),
                body.getOrDefault("note", "").toString()
        ));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",   order.getId());
        resp.put("code", order.getCode());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders/{orderId}/receive")
    @Operation(summary = "Xác nhận nhận hàng → tạo lot, cập nhật tồn kho. " +
                         "Dùng ingredientCode (string) hoặc ingredientId (UUID).")
    public ResponseEntity<Map<String, Object>> receiveOrder(
            @PathVariable UUID orderId,
            @RequestBody List<Map<String, Object>> body) {

        List<ReceiveLineRequest> lines = body.stream().map(item -> {
            String code = item.containsKey("ingredientCode")
                          ? item.get("ingredientCode").toString() : null;
            UUID   id   = (code == null || code.isBlank()) && item.containsKey("ingredientId")
                          ? UUID.fromString(item.get("ingredientId").toString()) : null;
            return new ReceiveLineRequest(
                    id,
                    code,
                    item.get("purchaseUnit").toString(),
                    new BigDecimal(item.get("qtyOrdered").toString()),
                    new BigDecimal(item.get("qtyReceived").toString()),
                    new BigDecimal(item.get("unitPrice").toString()),
                    item.get("expiryDate") != null
                            ? java.time.LocalDate.parse(item.get("expiryDate").toString())
                            : null,
                    item.getOrDefault("note", "").toString()
            );
        }).collect(Collectors.toList());

        purchaseOrderService.receiveOrder(orderId, lines);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "OK");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders/{orderId}/payment")
    @Operation(summary = "Ghi nhận thanh toán công nợ")
    public ResponseEntity<Map<String, Object>> recordPayment(
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body) {

        PurchaseOrder order = purchaseOrderService.recordPayment(
                orderId,
                new BigDecimal(body.get("amount").toString())
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code",          order.getCode());
        resp.put("totalAmount",   order.getTotalAmount());
        resp.put("paidAmount",    order.getPaidAmount());
        resp.put("debtAmount",    order.getDebtAmount());
        resp.put("paymentStatus", order.getPaymentStatus().name());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/orders/unpaid")
    @Operation(summary = "Danh sách đơn chưa thanh toán (công nợ)")
    public List<Map<String, Object>> getUnpaidOrders() {
        return purchaseOrderRepository
                .findAllByPaymentStatusNot(
                        com.bakery.common.entity.enums.PaymentStatus.PAID)
                .stream()
                .map(po -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",            po.getId());
                    m.put("code",          po.getCode());
                    m.put("supplier",      po.getSupplier().getName());
                    m.put("orderDate",     po.getOrderDate().toString());
                    m.put("totalAmount",   po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO);
                    m.put("paidAmount",    po.getPaidAmount());
                    m.put("debtAmount",    po.getDebtAmount());
                    m.put("paymentStatus", po.getPaymentStatus().name());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── Nguyên liệu (lookup helper) ───────────────────────────

    @GetMapping("/ingredients")
    @Operation(summary = "Danh sách nguyên liệu (để lấy code dùng khi nhập kho)")
    public List<Map<String, Object>> getIngredients(
            @RequestParam(required = false) String q) {
        return ingredientRepository.findAllByIsActiveTrue().stream()
                .filter(i -> q == null || i.getCode().toLowerCase().contains(q.toLowerCase())
                                       || i.getName().toLowerCase().contains(q.toLowerCase()))
                .map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",       i.getId());
                    m.put("code",     i.getCode());
                    m.put("name",     i.getName());
                    m.put("baseUnit", i.getBaseUnit());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── Tồn kho ───────────────────────────────────────────────

    @GetMapping("/stock")
    @Operation(summary = "Tồn kho nguyên liệu hiện tại")
    public List<Map<String, Object>> getStock() {
        return purchaseOrderService.getStock().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ingredientCode", s.getIngredient().getCode());
                    m.put("ingredientName", s.getIngredient().getName());
                    m.put("unit",           s.getIngredient().getBaseUnit());
                    m.put("qtyOnHand",      s.getQtyOnHand());
                    m.put("qtyReserved",    s.getQtyReserved());
                    m.put("qtyAvailable",   s.getQtyAvailable() != null
                            ? s.getQtyAvailable()
                            : s.getQtyOnHand());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/stock/lots")
    @Operation(summary = "Xem danh sách lô (lot) của 1 nguyên liệu")
    public List<Map<String, Object>> getLots(@RequestParam String ingredientCode) {
        Ingredient ingredient = ingredientRepository.findByCode(ingredientCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy: " + ingredientCode));

        return stockLotRepository
                .findAllByIngredientIdAndStatus(
                        ingredient.getId(),
                        com.bakery.common.entity.enums.StockLotStatus.AVAILABLE)
                .stream()
                .map(lot -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("barcode",       lot.getBarcode());
                    m.put("qtyInBaseUnit", lot.getQtyInBaseUnit());
                    m.put("qtyRemaining",  lot.getQtyRemaining());
                    m.put("unitPrice",     lot.getUnitPrice());
                    m.put("receivedDate",  lot.getReceivedDate().toString());
                    m.put("expiryDate",    lot.getExpiryDate() != null
                            ? lot.getExpiryDate().toString()
                            : "N/A");
                    m.put("status",        lot.getStatus().name());
                    return m;
                })
                .collect(Collectors.toList());
    }
}