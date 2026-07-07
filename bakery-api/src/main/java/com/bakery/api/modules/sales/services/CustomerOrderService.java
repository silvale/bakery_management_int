package com.bakery.api.modules.sales.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.framework.services.CodeSequenceService;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import com.bakery.api.modules.sales.entities.CustomerOrder;
import com.bakery.api.modules.sales.entities.CustomerOrderLine;
import com.bakery.api.modules.sales.entities.CustomerOrderLineAddon;
import com.bakery.api.modules.sales.entities.CustomerOrderPayment;
import com.bakery.api.modules.sales.repositories.CustomerOrderLineAddonRepository;
import com.bakery.api.modules.sales.repositories.CustomerOrderLineRepository;
import com.bakery.api.modules.sales.repositories.CustomerOrderRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private final CustomerOrderRepository        customerOrderRepository;
    private final CustomerOrderLineRepository    customerOrderLineRepository;
    private final ProductRepository              productRepository;
    private final IngredientRepository           ingredientRepository;
    private final CustomerOrderLineAddonRepository addonRepository;
    private final ActivityLogRepository          activityLogRepository;
    private final CodeSequenceService            codeSequenceService;

    public record OrderLineRequest(UUID productId, BigDecimal qty, BigDecimal unitPrice, String note) {}

    @Transactional
    public Map<String, Object> createOrder(String customerName, String customerPhone, LocalDate deliveryDate,
                                            String note, String createdBy, List<OrderLineRequest> lines) {
        LocalDate today = LocalDate.now();
        String code = codeSequenceService.nextCustomerOrderCode(today);

        CustomerOrder order = CustomerOrder.builder()
            .code(code)
            .customerName(customerName)
            .customerPhone(customerPhone)
            .deliveryDate(deliveryDate)
            .note(note)
            .status("PENDING")
            .paymentStatus("UNPAID")
            .depositAmount(BigDecimal.ZERO)
            .paidAmount(BigDecimal.ZERO)
            .createdBy(createdBy != null ? createdBy : "system")
            .createdAt(OffsetDateTime.now())
            .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderLineRequest lineReq : lines) {
            Product product = productRepository.findById(lineReq.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + lineReq.productId()));

            CustomerOrderLine line = CustomerOrderLine.builder()
                .order(order)
                .product(product)
                .qty(lineReq.qty())
                .unitPrice(lineReq.unitPrice())
                .note(lineReq.note())
                .build();
            order.getLines().add(line);

            totalAmount = totalAmount.add(lineReq.qty().multiply(lineReq.unitPrice()));
        }

        order.setTotalAmount(totalAmount);
        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(createdBy != null ? createdBy : "system")
            .action("CREATE_ORDER")
            .entityType("CustomerOrder")
            .entityId(order.getId())
            .entityCode(order.getCode())
            .newStatus("PENDING")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Created CustomerOrder {} total={}", code, totalAmount);
        return toDetailMap(order);
    }

    @Transactional
    public Map<String, Object> confirmOrder(UUID id, String confirmedBy) {
        CustomerOrder order = customerOrderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + id));
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Order is not PENDING, current status: " + order.getStatus());
        }
        order.setStatus("CONFIRMED");
        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(confirmedBy)
            .action("CONFIRM_ORDER")
            .entityType("CustomerOrder")
            .entityId(id)
            .entityCode(order.getCode())
            .oldStatus("PENDING")
            .newStatus("CONFIRMED")
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(order);
    }

    @Transactional
    public Map<String, Object> cancelOrder(UUID id, String cancelledBy, String reason) {
        CustomerOrder order = customerOrderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + id));
        String oldStatus = order.getStatus();
        order.setStatus("CANCELLED");
        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(cancelledBy)
            .action("CANCEL_ORDER")
            .entityType("CustomerOrder")
            .entityId(id)
            .entityCode(order.getCode())
            .oldStatus(oldStatus)
            .newStatus("CANCELLED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(order);
    }

    @Transactional
    public Map<String, Object> updateDeliveryStatus(UUID id, String status, String updatedBy) {
        CustomerOrder order = customerOrderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + id));
        String oldStatus = order.getStatus();
        order.setStatus(status);
        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(updatedBy)
            .action("UPDATE_ORDER_STATUS")
            .entityType("CustomerOrder")
            .entityId(id)
            .entityCode(order.getCode())
            .oldStatus(oldStatus)
            .newStatus(status)
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(order);
    }

    @Transactional
    public Map<String, Object> recordPayment(UUID orderId, String paymentType, BigDecimal amount,
                                              LocalDate paymentDate, String note, String recordedBy) {
        CustomerOrder order = customerOrderRepository.findByIdWithDetails(orderId)
            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + orderId));

        CustomerOrderPayment payment = CustomerOrderPayment.builder()
            .order(order)
            .paymentType(paymentType != null ? paymentType : "BANK_TRANSFER")
            .amount(amount)
            .paymentDate(paymentDate != null ? paymentDate : LocalDate.now())
            .note(note)
            .recordedBy(recordedBy != null ? recordedBy : "system")
            .createdAt(OffsetDateTime.now())
            .build();

        order.getPayments().add(payment);
        order.setPaidAmount(order.getPaidAmount().add(amount));

        // Recompute paymentStatus
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = order.getPaidAmount();
        BigDecimal deposit = order.getDepositAmount();

        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            order.setPaymentStatus("UNPAID");
        } else if (paid.compareTo(total) >= 0) {
            order.setPaymentStatus("PAID");
        } else if (deposit.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(deposit) == 0) {
            order.setPaymentStatus("DEPOSIT");
        } else {
            order.setPaymentStatus("PARTIAL");
        }

        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(recordedBy != null ? recordedBy : "system")
            .action("RECORD_PAYMENT")
            .entityType("CustomerOrder")
            .entityId(orderId)
            .entityCode(order.getCode())
            .note("amount=" + amount + " type=" + paymentType)
            .createdAt(OffsetDateTime.now())
            .build());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getId());
        result.put("orderCode", order.getCode());
        result.put("totalAmount", order.getTotalAmount());
        result.put("paidAmount", order.getPaidAmount());
        result.put("debtAmount", order.getDebtAmount());
        result.put("paymentStatus", order.getPaymentStatus());
        result.put("paymentRecorded", amount);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderWithDetails(UUID id) {
        CustomerOrder order = customerOrderRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + id));
        return toDetailMap(order);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOrders(String status, LocalDate deliveryDate, String paymentStatus) {
        List<CustomerOrder> orders;

        if (deliveryDate != null) {
            orders = customerOrderRepository.findAllByDeliveryDateOrderByCreatedAtAsc(deliveryDate);
            if (status != null) {
                orders = orders.stream().filter(o -> status.equals(o.getStatus())).collect(Collectors.toList());
            }
        } else if (status != null) {
            orders = customerOrderRepository.findAllByStatusOrderByDeliveryDateAsc(status);
        } else {
            orders = customerOrderRepository.findAll().stream()
                .sorted(Comparator.comparing(CustomerOrder::getDeliveryDate))
                .collect(Collectors.toList());
        }

        if (paymentStatus != null) {
            orders = orders.stream()
                .filter(o -> paymentStatus.equals(o.getPaymentStatus()))
                .collect(Collectors.toList());
        }

        return orders.stream().map(this::toMap).collect(Collectors.toList());
    }

    // =========================================================================
    // SHEET_CAKE — Add-on topping / NL đặc thù
    // =========================================================================

    /**
     * Thêm addon (NL hoặc ACCESSORY) vào 1 line của đơn SHEET_CAKE.
     * Chỉ cho phép khi đơn đang ở trạng thái PENDING hoặc CONFIRMED.
     *
     * addonType = 'INGREDIENT' → ingredientId required
     * addonType = 'ACCESSORY'  → productId required (product phải là type ACCESSORY)
     */
    @Transactional
    public Map<String, Object> addAddon(UUID lineId, String addonType, UUID ingredientId,
                                        UUID productId, BigDecimal qty, String unit,
                                        String note, String createdBy) {
        CustomerOrderLine line = customerOrderLineRepository.findByIdWithOrder(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Order line not found: " + lineId));

        // Validate đơn chưa IN_PRODUCTION
        CustomerOrder order = line.getOrder();
        if ("IN_PRODUCTION".equals(order.getStatus()) || "READY".equals(order.getStatus())
                || "DELIVERED".equals(order.getStatus())) {
            throw new IllegalStateException("Đơn đang sản xuất/giao, không thể thêm addon. Status: " + order.getStatus());
        }

        Ingredient ing = null;
        Product prod = null;

        if ("INGREDIENT".equals(addonType)) {
            if (ingredientId == null) throw new IllegalArgumentException("ingredientId required cho INGREDIENT addon");
            ing = ingredientRepository.findById(ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("Ingredient not found: " + ingredientId));
        } else if ("ACCESSORY".equals(addonType)) {
            if (productId == null) throw new IllegalArgumentException("productId required cho ACCESSORY addon");
            prod = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        } else {
            throw new IllegalArgumentException("addonType không hợp lệ: " + addonType);
        }

        CustomerOrderLineAddon addon = CustomerOrderLineAddon.builder()
                .line(line)
                .addonType(addonType)
                .ingredient(ing)
                .product(prod)
                .qty(qty)
                .unit(unit != null ? unit : "g")
                .note(note)
                .createdBy(createdBy != null ? createdBy : "system")
                .createdAt(OffsetDateTime.now())
                .build();

        addonRepository.save(addon);
        log.info("Added addon {} to line {}", addonType, lineId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addonId", addon.getId());
        result.put("lineId", lineId);
        result.put("addonType", addonType);
        result.put("ingredientId", ing != null ? ing.getId() : null);
        result.put("ingredientCode", ing != null ? ing.getCode() : null);
        result.put("productId", prod != null ? prod.getId() : null);
        result.put("productCode", prod != null ? prod.getCode() : null);
        result.put("qty", qty);
        result.put("unit", unit);
        return result;
    }

    /**
     * Lock đơn SHEET_CAKE vào sản xuất: CONFIRMED → IN_PRODUCTION.
     * Sau bước này FifoEngine sẽ dùng addons để trừ kho NL.
     * Order bị lock — không thể thêm/sửa addon.
     */
    @Transactional
    public Map<String, Object> lockForProduction(UUID id, String lockedBy) {
        CustomerOrder order = customerOrderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("CustomerOrder not found: " + id));

        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException(
                "Chỉ đơn CONFIRMED mới có thể lock sản xuất. Status hiện tại: " + order.getStatus());
        }

        order.setStatus("IN_PRODUCTION");
        customerOrderRepository.save(order);

        activityLogRepository.save(ActivityLog.builder()
                .performedBy(lockedBy != null ? lockedBy : "system")
                .action("LOCK_FOR_PRODUCTION")
                .entityType("CustomerOrder")
                .entityId(id)
                .entityCode(order.getCode())
                .oldStatus("CONFIRMED")
                .newStatus("IN_PRODUCTION")
                .note("Order locked — addon list finalized, FifoEngine sẽ trừ kho")
                .createdAt(OffsetDateTime.now())
                .build());

        log.info("CustomerOrder {} → IN_PRODUCTION (locked by {})", order.getCode(), lockedBy);
        return toMap(order);
    }

    /**
     * Danh sách đơn SHEET_CAKE cần thợ bánh review trước giờ chốt sổ.
     * Lấy các đơn CONFIRMED có delivery_date = targetDate và có product SHEET_CAKE.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSheetCakePendingProduction(LocalDate targetDate) {
        return customerOrderRepository
                .findAllByDeliveryDateAndStatus(targetDate, "CONFIRMED")
                .stream()
                .filter(order -> order.getLines().stream()
                        .anyMatch(l -> l.getProduct() != null
                                && l.getProduct().getProductType() != null
                                && "SHEET_CAKE".equals(l.getProduct().getProductType().name())))
                .map(this::toDetailMapWithAddons)
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(CustomerOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("code", o.getCode());
        m.put("customerName", o.getCustomerName());
        m.put("customerPhone", o.getCustomerPhone());
        m.put("deliveryDate", o.getDeliveryDate().toString());
        m.put("status", o.getStatus());
        m.put("paymentStatus", o.getPaymentStatus());
        m.put("totalAmount", o.getTotalAmount());
        m.put("depositAmount", o.getDepositAmount());
        m.put("paidAmount", o.getPaidAmount());
        m.put("debtAmount", o.getDebtAmount());
        m.put("createdBy", o.getCreatedBy());
        m.put("createdAt", o.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toDetailMap(CustomerOrder o) {
        return toDetailMapWithAddons(o);
    }

    private Map<String, Object> toDetailMapWithAddons(CustomerOrder o) {
        Map<String, Object> m = toMap(o);
        m.put("note", o.getNote());
        m.put("lines", o.getLines().stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("id", l.getId());
            lm.put("productId", l.getProduct().getId());
            lm.put("productCode", l.getProduct().getCode());
            lm.put("productName", l.getProduct().getName());
            lm.put("productType", l.getProduct().getProductType() != null ? l.getProduct().getProductType().name() : null);
            lm.put("qty", l.getQty());
            lm.put("unitPrice", l.getUnitPrice());
            lm.put("totalPrice", l.getTotalPrice());
            lm.put("note", l.getNote());
            // Addons (chỉ có ý nghĩa với SHEET_CAKE)
            lm.put("addons", l.getAddons().stream().map(a -> {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("id", a.getId());
                am.put("addonType", a.getAddonType());
                am.put("ingredientId", a.getIngredient() != null ? a.getIngredient().getId() : null);
                am.put("ingredientCode", a.getIngredient() != null ? a.getIngredient().getCode() : null);
                am.put("productId", a.getProduct() != null ? a.getProduct().getId() : null);
                am.put("productCode", a.getProduct() != null ? a.getProduct().getCode() : null);
                am.put("qty", a.getQty());
                am.put("unit", a.getUnit());
                am.put("note", a.getNote());
                return am;
            }).collect(Collectors.toList()));
            return lm;
        }).collect(Collectors.toList()));
        m.put("payments", o.getPayments().stream().map(p -> {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("paymentType", p.getPaymentType());
            pm.put("amount", p.getAmount());
            pm.put("paymentDate", p.getPaymentDate().toString());
            pm.put("note", p.getNote());
            pm.put("recordedBy", p.getRecordedBy());
            pm.put("createdAt", p.getCreatedAt().toString());
            return pm;
        }).collect(Collectors.toList()));
        return m;
    }
}
