package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.ReferenceType;
import com.bakery.common.entity.enums.TransactionType;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final BranchRepository branchRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final IngredientStockLotRepository ingredientStockLotRepository;
    private final ProductStockLotRepository productStockLotRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    @Transactional
    public Map<String, Object> createAdjustment(UUID branchId, String itemType, UUID ingredientId,
                                                 UUID productId, UUID lotId, BigDecimal qtyAfter,
                                                 String reason, String createdBy) {
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));

        BigDecimal qtyBefore;

        if ("INGREDIENT".equals(itemType)) {
            IngredientStockLot lot = ingredientStockLotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("IngredientStockLot not found: " + lotId));
            qtyBefore = lot.getQtyRemaining();
        } else if ("PRODUCT".equals(itemType)) {
            ProductStockLot lot = productStockLotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("ProductStockLot not found: " + lotId));
            qtyBefore = lot.getQtyRemaining();
        } else {
            throw new IllegalArgumentException("itemType must be INGREDIENT or PRODUCT");
        }

        String code = codeSequenceService.nextAdjustmentCode(LocalDate.now());

        InventoryAdjustment.InventoryAdjustmentBuilder builder = InventoryAdjustment.builder()
            .code(code)
            .branch(branch)
            .itemType(itemType)
            .lotId(lotId)
            .qtyBefore(qtyBefore)
            .qtyAfter(qtyAfter)
            .reason(reason)
            .status("PENDING")
            .createdBy(createdBy != null ? createdBy : "system")
            .createdAt(OffsetDateTime.now());

        if ("INGREDIENT".equals(itemType) && ingredientId != null) {
            Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found: " + ingredientId));
            builder.ingredient(ingredient);
        }
        if ("PRODUCT".equals(itemType) && productId != null) {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
            builder.product(product);
        }

        InventoryAdjustment adjustment = builder.build();
        inventoryAdjustmentRepository.save(adjustment);

        log.info("Created InventoryAdjustment {} type={} lot={} before={} after={}",
            code, itemType, lotId, qtyBefore, qtyAfter);
        return toDetailMap(adjustment);
    }

    @Transactional
    public Map<String, Object> approveAdjustment(UUID id, String approvedBy) {
        InventoryAdjustment adj = inventoryAdjustmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryAdjustment not found: " + id));
        if (!"PENDING".equals(adj.getStatus())) {
            throw new IllegalStateException("Adjustment is not PENDING, current status: " + adj.getStatus());
        }

        adj.setStatus("APPROVED");
        adj.setApprovedBy(approvedBy);
        adj.setApprovedAt(OffsetDateTime.now());

        Branch branch = adj.getBranch();
        BigDecimal delta = adj.getQtyAfter().subtract(adj.getQtyBefore());
        TransactionType movementType = TransactionType.ADJUSTMENT;
        ReferenceType movementReason = delta.compareTo(BigDecimal.ZERO) > 0
            ? ReferenceType.INCREASE : ReferenceType.DECREASE;
        BigDecimal absQty = delta.abs();

        if ("INGREDIENT".equals(adj.getItemType())) {
            IngredientStockLot lot = ingredientStockLotRepository.findById(adj.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("IngredientStockLot not found: " + adj.getLotId()));
            lot.setQtyRemaining(adj.getQtyAfter());
            lot.setIsDepleted(adj.getQtyAfter().compareTo(BigDecimal.ZERO) <= 0);
            ingredientStockLotRepository.save(lot);

            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("INGREDIENT")
                .ingredient(adj.getIngredient())
                .lotId(lot.getId())
                .transactionType(movementType)
                .referenceType(movementReason)
                .qty(absQty)
                .unit("g")
                .sourceType("ADJUSTMENT")
                .sourceId(id)
                .note(adj.getReason())
                .createdBy(approvedBy)
                .createdAt(OffsetDateTime.now())
                .build());

        } else if ("PRODUCT".equals(adj.getItemType())) {
            ProductStockLot lot = productStockLotRepository.findById(adj.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("ProductStockLot not found: " + adj.getLotId()));
            lot.setQtyRemaining(adj.getQtyAfter());
            lot.setIsDepleted(adj.getQtyAfter().compareTo(BigDecimal.ZERO) <= 0);
            productStockLotRepository.save(lot);

            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("PRODUCT")
                .product(adj.getProduct())
                .lotId(lot.getId())
                .transactionType(movementType)
                .referenceType(movementReason)
                .qty(absQty)
                .unit("PCS")
                .sourceType("ADJUSTMENT")
                .sourceId(id)
                .note(adj.getReason())
                .createdBy(approvedBy)
                .createdAt(OffsetDateTime.now())
                .build());
        }

        inventoryAdjustmentRepository.save(adj);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(approvedBy)
            .action("APPROVE_ADJUSTMENT")
            .entityType("InventoryAdjustment")
            .entityId(id)
            .entityCode(adj.getCode())
            .oldStatus("PENDING")
            .newStatus("APPROVED")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Approved InventoryAdjustment {} by {} delta={}", adj.getCode(), approvedBy, delta);
        return toDetailMap(adj);
    }

    @Transactional
    public Map<String, Object> rejectAdjustment(UUID id, String rejectedBy, String reason) {
        InventoryAdjustment adj = inventoryAdjustmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryAdjustment not found: " + id));
        if (!"PENDING".equals(adj.getStatus())) {
            throw new IllegalStateException("Adjustment is not PENDING, current status: " + adj.getStatus());
        }
        adj.setStatus("REJECTED");
        inventoryAdjustmentRepository.save(adj);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(rejectedBy)
            .action("REJECT_ADJUSTMENT")
            .entityType("InventoryAdjustment")
            .entityId(id)
            .entityCode(adj.getCode())
            .oldStatus("PENDING")
            .newStatus("REJECTED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toDetailMap(adj);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdjustment(UUID id) {
        InventoryAdjustment adj = inventoryAdjustmentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryAdjustment not found: " + id));
        return toDetailMap(adj);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdjustments(String status, UUID branchId) {
        List<InventoryAdjustment> list;
        if (branchId != null && status != null) {
            list = inventoryAdjustmentRepository.findAllByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status);
        } else if (branchId != null) {
            list = inventoryAdjustmentRepository.findAllByBranchIdOrderByCreatedAtDesc(branchId);
        } else if (status != null) {
            list = inventoryAdjustmentRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            list = inventoryAdjustmentRepository.findAll().stream()
                .sorted(Comparator.comparing(InventoryAdjustment::getCreatedAt).reversed())
                .collect(Collectors.toList());
        }
        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(InventoryAdjustment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("code", a.getCode());
        m.put("branchId", a.getBranch().getId());
        m.put("branchName", a.getBranch().getName());
        m.put("itemType", a.getItemType());
        m.put("lotId", a.getLotId());
        m.put("qtyBefore", a.getQtyBefore());
        m.put("qtyAfter", a.getQtyAfter());
        m.put("qtyDelta", a.getQtyDelta());
        m.put("reason", a.getReason());
        m.put("status", a.getStatus());
        m.put("createdBy", a.getCreatedBy());
        m.put("createdAt", a.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toDetailMap(InventoryAdjustment a) {
        Map<String, Object> m = toMap(a);
        m.put("approvedBy", a.getApprovedBy());
        m.put("approvedAt", a.getApprovedAt() != null ? a.getApprovedAt().toString() : null);
        if (a.getIngredient() != null) {
            m.put("ingredientId", a.getIngredient().getId());
            m.put("ingredientCode", a.getIngredient().getCode());
        }
        if (a.getProduct() != null) {
            m.put("productId", a.getProduct().getId());
            m.put("productCode", a.getProduct().getCode());
        }
        return m;
    }
}
