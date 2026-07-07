package com.bakery.api.modules.partner.controllers;

import com.bakery.api.modules.partner.entities.PurchaseOrder;
import com.bakery.api.modules.partner.entities.Supplier;
import com.bakery.api.framework.enums.PaymentStatus;
import com.bakery.api.modules.partner.repositories.PurchaseOrderRepository;
import com.bakery.api.modules.partner.repositories.SupplierRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API xem lịch sử mua hàng + công nợ nhà cung cấp.
 *
 * CRUD nhà cung cấp nằm ở SupplierAdminController (/admin/suppliers).
 * Controller này cung cấp thêm:
 *   GET /admin/suppliers/{id}/summary  — tổng hợp: tổng tiền, đã trả, công nợ, lịch sử PO
 *   GET /admin/suppliers/debts         — tất cả NCC còn công nợ
 */
@RestController
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
@Tag(name = "Supplier Query", description = "Lịch sử mua hàng + công nợ nhà cung cấp")
public class SupplierQueryController {

    private final SupplierRepository      supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    // ── Summary 1 NCC ─────────────────────────────────────────

    @GetMapping("/{id}/summary")
    @Operation(summary = "Tổng hợp lịch sử PO + công nợ của 1 nhà cung cấp")
    public ResponseEntity<Map<String, Object>> getSupplierSummary(@PathVariable UUID id) {

        Supplier supplier = supplierRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy NCC: " + id));

        List<PurchaseOrder> orders = purchaseOrderRepository
            .findAllBySupplierIdOrderByOrderDateDesc(supplier.getId());

        BigDecimal totalAmount = orders.stream()
            .map(po -> po.getTotalAmount() != null ? po.getTotalAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = orders.stream()
            .map(po -> po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebt = purchaseOrderRepository.sumDebtBySupplierId(supplier.getId());

        List<Map<String, Object>> poList = orders.stream().map(po -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",             po.getId());
            m.put("code",           po.getCode());
            m.put("orderDate",      po.getOrderDate().toString());
            m.put("receivedDate",   po.getReceivedDate() != null
                ? po.getReceivedDate().toString() : null);
            m.put("totalAmount",    po.getTotalAmount());
            m.put("paidAmount",     po.getPaidAmount());
            m.put("debtAmount",     po.getDebtAmount());
            m.put("paymentStatus",  po.getPaymentStatus().name());
            m.put("note",           po.getNote());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id",           supplier.getId());
        resp.put("code",         supplier.getCode());
        resp.put("name",         supplier.getName());
        resp.put("phone",        supplier.getPhone());
        resp.put("address",      supplier.getAddress());
        resp.put("isActive",     supplier.getIsActive());
        resp.put("totalOrders",  orders.size());
        resp.put("totalAmount",  totalAmount);
        resp.put("totalPaid",    totalPaid);
        resp.put("totalDebt",    totalDebt);
        resp.put("orders",       poList);

        return ResponseEntity.ok(resp);
    }

    // ── Tất cả NCC còn công nợ ───────────────────────────────

    @GetMapping("/debts")
    @Operation(summary = "Danh sách tất cả nhà cung cấp còn công nợ (chưa thanh toán đủ)")
    public List<Map<String, Object>> getAllDebts() {

        // Lấy tất cả PO chưa thanh toán đủ
        List<PurchaseOrder> unpaidOrders = purchaseOrderRepository
            .findAllByPaymentStatusNot(PaymentStatus.PAID);

        // Group by supplier
        Map<UUID, List<PurchaseOrder>> bySupplier = unpaidOrders.stream()
            .collect(Collectors.groupingBy(po -> po.getSupplier().getId()));

        return bySupplier.entrySet().stream()
            .map(entry -> {
                Supplier sup = entry.getValue().get(0).getSupplier();
                List<PurchaseOrder> pos = entry.getValue();

                BigDecimal totalDebt = pos.stream()
                    .map(PurchaseOrder::getDebtAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                long overdueCount = pos.stream()
                    .filter(po -> po.getPaymentStatus() == PaymentStatus.UNPAID)
                    .count();

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("supplierId",   sup.getId());
                m.put("supplierCode", sup.getCode());
                m.put("supplierName", sup.getName());
                m.put("phone",        sup.getPhone());
                m.put("openOrders",   pos.size());
                m.put("unpaidOrders", overdueCount);
                m.put("totalDebt",    totalDebt);
                // Oldest unpaid PO date
                pos.stream()
                    .map(PurchaseOrder::getOrderDate)
                    .min(Comparator.naturalOrder())
                    .ifPresent(d -> m.put("oldestUnpaidDate", d.toString()));
                return m;
            })
            .sorted(Comparator.<Map<String, Object>, BigDecimal>comparing(
                m -> (BigDecimal) m.get("totalDebt")).reversed())
            .collect(Collectors.toList());
    }
}
