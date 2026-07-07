package com.bakery.api.modules.inventory.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.ReferenceType;
import com.bakery.api.framework.enums.TransactionType;
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
import com.bakery.api.modules.inventory.entities.IngredientStockLot;
import com.bakery.api.modules.inventory.entities.InventoryMovement;
import com.bakery.api.modules.inventory.entities.InventoryWriteOff;
import com.bakery.api.modules.inventory.entities.ProductStockLot;
import com.bakery.api.modules.inventory.repositories.IngredientStockLotRepository;
import com.bakery.api.modules.inventory.repositories.InventoryMovementRepository;
import com.bakery.api.modules.inventory.repositories.InventoryWriteOffRepository;
import com.bakery.api.modules.inventory.repositories.ProductStockLotRepository;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWriteOffService {

    private final InventoryWriteOffRepository inventoryWriteOffRepository;
    private final BranchRepository branchRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final IngredientStockLotRepository ingredientStockLotRepository;
    private final ProductStockLotRepository productStockLotRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    @Transactional
    public Map<String, Object> createWriteOff(UUID branchId, String itemType, UUID ingredientId,
                                               UUID productId, UUID lotId, BigDecimal qty, String unit,
                                               String reasonType, String reasonNote, String createdBy) {
        Branch branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));

        // Validate lot exists
        if ("INGREDIENT".equals(itemType)) {
            ingredientStockLotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("IngredientStockLot not found: " + lotId));
        } else if ("PRODUCT".equals(itemType)) {
            productStockLotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("ProductStockLot not found: " + lotId));
        } else {
            throw new IllegalArgumentException("itemType must be INGREDIENT or PRODUCT");
        }

        String code = codeSequenceService.nextWriteOffCode(LocalDate.now());

        InventoryWriteOff.InventoryWriteOffBuilder builder = InventoryWriteOff.builder()
            .code(code)
            .branch(branch)
            .itemType(itemType)
            .lotId(lotId)
            .qty(qty)
            .unit(unit)
            .reasonType(reasonType)
            .reasonNote(reasonNote)
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

        InventoryWriteOff writeOff = builder.build();
        inventoryWriteOffRepository.save(writeOff);

        log.info("Created InventoryWriteOff {} type={} lot={} qty={}", code, itemType, lotId, qty);
        return toDetailMap(writeOff);
    }

    @Transactional
    public Map<String, Object> approveWriteOff(UUID id, String approvedBy) {
        InventoryWriteOff writeOff = inventoryWriteOffRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryWriteOff not found: " + id));
        if (!"PENDING".equals(writeOff.getStatus())) {
            throw new IllegalStateException("WriteOff is not PENDING, current status: " + writeOff.getStatus());
        }

        writeOff.setStatus("APPROVED");
        writeOff.setApprovedBy(approvedBy);
        writeOff.setApprovedAt(OffsetDateTime.now());

        Branch branch = writeOff.getBranch();

        // Deduct from lot
        if ("INGREDIENT".equals(writeOff.getItemType())) {
            IngredientStockLot lot = ingredientStockLotRepository.findById(writeOff.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("IngredientStockLot not found: " + writeOff.getLotId()));
            lot.consume(writeOff.getQty());
            ingredientStockLotRepository.save(lot);

            // Write InventoryMovement
            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("INGREDIENT")
                .ingredient(writeOff.getIngredient())
                .lotId(lot.getId())
                .transactionType(TransactionType.DISCARD)
                .referenceType(resolveWriteOffReferenceType(writeOff.getReasonType()))
                .qty(writeOff.getQty())
                .unit(writeOff.getUnit())
                .sourceType("WRITE_OFF")
                .sourceId(id)
                .referenceCode(writeOff.getCode())
                .note(writeOff.getReasonNote())
                .createdBy(approvedBy)
                .createdAt(OffsetDateTime.now())
                .build());

        } else if ("PRODUCT".equals(writeOff.getItemType())) {
            ProductStockLot lot = productStockLotRepository.findById(writeOff.getLotId())
                .orElseThrow(() -> new IllegalArgumentException("ProductStockLot not found: " + writeOff.getLotId()));
            BigDecimal newQty = lot.getQtyRemaining().subtract(writeOff.getQty());
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                newQty = BigDecimal.ZERO;
                lot.setIsDepleted(true);
            }
            lot.setQtyRemaining(newQty);
            productStockLotRepository.save(lot);

            // Write InventoryMovement
            inventoryMovementRepository.save(InventoryMovement.builder()
                .branch(branch)
                .itemType("PRODUCT")
                .product(writeOff.getProduct())
                .lotId(lot.getId())
                .transactionType(TransactionType.DISCARD)
                .referenceType(resolveWriteOffReferenceType(writeOff.getReasonType()))
                .qty(writeOff.getQty())
                .unit(writeOff.getUnit())
                .sourceType("WRITE_OFF")
                .sourceId(id)
                .referenceCode(writeOff.getCode())
                .note(writeOff.getReasonNote())
                .createdBy(approvedBy)
                .createdAt(OffsetDateTime.now())
                .build());
        }

        inventoryWriteOffRepository.save(writeOff);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(approvedBy)
            .action("APPROVE_WRITE_OFF")
            .entityType("InventoryWriteOff")
            .entityId(id)
            .entityCode(writeOff.getCode())
            .oldStatus("PENDING")
            .newStatus("APPROVED")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Approved InventoryWriteOff {} by {}", writeOff.getCode(), approvedBy);
        return toDetailMap(writeOff);
    }

    @Transactional
    public Map<String, Object> rejectWriteOff(UUID id, String rejectedBy, String reason) {
        InventoryWriteOff writeOff = inventoryWriteOffRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryWriteOff not found: " + id));
        if (!"PENDING".equals(writeOff.getStatus())) {
            throw new IllegalStateException("WriteOff is not PENDING, current status: " + writeOff.getStatus());
        }
        writeOff.setStatus("REJECTED");
        inventoryWriteOffRepository.save(writeOff);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(rejectedBy)
            .action("REJECT_WRITE_OFF")
            .entityType("InventoryWriteOff")
            .entityId(id)
            .entityCode(writeOff.getCode())
            .oldStatus("PENDING")
            .newStatus("REJECTED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toDetailMap(writeOff);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listWriteOffs(String status, UUID branchId) {
        List<InventoryWriteOff> list;
        if (branchId != null && status != null) {
            list = inventoryWriteOffRepository.findAllByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status);
        } else if (branchId != null) {
            list = inventoryWriteOffRepository.findAllByBranchIdOrderByCreatedAtDesc(branchId);
        } else if (status != null) {
            list = inventoryWriteOffRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            list = inventoryWriteOffRepository.findAll().stream()
                .sorted(Comparator.comparing(InventoryWriteOff::getCreatedAt).reversed())
                .collect(Collectors.toList());
        }
        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWriteOff(UUID id) {
        InventoryWriteOff writeOff = inventoryWriteOffRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("InventoryWriteOff not found: " + id));
        return toDetailMap(writeOff);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(InventoryWriteOff w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("code", w.getCode());
        m.put("branchId", w.getBranch().getId());
        m.put("branchName", w.getBranch().getName());
        m.put("itemType", w.getItemType());
        m.put("lotId", w.getLotId());
        m.put("qty", w.getQty());
        m.put("unit", w.getUnit());
        m.put("reasonType", w.getReasonType());
        m.put("status", w.getStatus());
        m.put("createdBy", w.getCreatedBy());
        m.put("createdAt", w.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toDetailMap(InventoryWriteOff w) {
        Map<String, Object> m = toMap(w);
        m.put("reasonNote", w.getReasonNote());
        m.put("approvedBy", w.getApprovedBy());
        m.put("approvedAt", w.getApprovedAt() != null ? w.getApprovedAt().toString() : null);
        if (w.getIngredient() != null) {
            m.put("ingredientId", w.getIngredient().getId());
            m.put("ingredientCode", w.getIngredient().getCode());
            m.put("ingredientName", w.getIngredient().getName());
        }
        if (w.getProduct() != null) {
            m.put("productId", w.getProduct().getId());
            m.put("productCode", w.getProduct().getCode());
            m.put("productName", w.getProduct().getName());
        }
        return m;
    }

    /** Map reasonType của WriteOff sang ReferenceType enum */
    private ReferenceType resolveWriteOffReferenceType(String reasonType) {
        if (reasonType == null) return ReferenceType.DAMAGED;
        return switch (reasonType.toUpperCase()) {
            case "EXPIRED" -> ReferenceType.EXPIRED;
            default        -> ReferenceType.DAMAGED;
        };
    }
}
