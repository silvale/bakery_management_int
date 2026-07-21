package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.production.repository.DeliveryRecordRepository;
import com.bakery.api.production.repository.ProductionAdjustmentRepository;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeLineRepository;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.framework.entity.AdjustmentSource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý điều chỉnh sản lượng (ProductionAdjustment).
 *
 * <p>Khi bếp trưởng APPROVE:
 *   INGREDIENT_VARIANCE → cộng/trừ NL kho bếp theo delta × recipe ingredient qty
 *   PRODUCTION_WASTAGE  → chỉ update DeliveryRecord.qtyProduced, không động NL
 *
 * <p>Admin có thể tạo adjustment bất kỳ lúc nào trước khi daily report FINALIZED.
 */
@Service
@RequiredArgsConstructor
public class ProductionAdjustmentService {

    private static final String KITCHEN_CODE = "KITCHEN";
    private static final String REF_TYPE = "PRODUCTION_ADJUSTMENT";

    private final ProductionAdjustmentRepository adjustmentRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final WarehouseRepository warehouseRepository;
    private final EntityManager entityManager;
    private final BakeryActorResolver actorResolver;

    // ── Queries ──────────────────────────────────────────────────

    public List<ProductionAdjustment> findByDeliveryRecord(UUID deliveryRecordId) {
        return adjustmentRepository.findByDeliveryRecordId(deliveryRecordId);
    }

    public List<ProductionAdjustment> findPending() {
        return adjustmentRepository.findByApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
    }

    // ── Admin tạo correction ─────────────────────────────────────

    /**
     * Admin tạo điều chỉnh sau khi bếp đã submit.
     * Không giới hạn số lần (khác với KITCHEN_COMPLETE chỉ 1 lần).
     */
    @Transactional
    public ProductionAdjustment createAdminCorrection(UUID deliveryRecordId,
            AdjustmentType adjustmentType, BigDecimal adjustedQty, String reason) {
        DeliveryRecord dr = deliveryRecordRepository.findById(deliveryRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryRecord", deliveryRecordId));

        BigDecimal originalQty = dr.getQtyProduced();

        ProductionAdjustment adj = new ProductionAdjustment();
        adj.setDeliveryRecord(dr);
        adj.setAdjustmentType(adjustmentType);
        adj.setSource(AdjustmentSource.ADMIN_CORRECTION);
        adj.setOriginalQty(originalQty);
        adj.setAdjustedQty(adjustedQty);
        adj.setDelta(adjustedQty.subtract(originalQty));
        adj.setReason(reason);
        adj.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        adj.setCreatedBy(actorResolver.currentUserId());
        return adjustmentRepository.save(adj);
    }

    // ── Bếp trưởng duyệt ────────────────────────────────────────

    /**
     * Bếp trưởng duyệt adjustment.
     *
     * <p>INGREDIENT_VARIANCE:
     *   delta > 0 → bếp lấy thêm NL → trừ thêm từ KITCHEN theo recipe
     *   delta < 0 → bếp dùng ít NL  → hoàn NL vào KITCHEN (tạo lot mới IN)
     *
     * <p>PRODUCTION_WASTAGE: chỉ update qtyProduced, không động NL.
     */
    @Transactional
    public ProductionAdjustment approve(UUID adjustmentId) {
        ProductionAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionAdjustment", adjustmentId));

        if (adj.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Adjustment không ở trạng thái PENDING_APPROVAL.");
        }

        // Cập nhật DeliveryRecord.qtyProduced
        DeliveryRecord dr = adj.getDeliveryRecord();
        dr.setQtyProduced(adj.getAdjustedQty());
        deliveryRecordRepository.save(dr);

        // Xử lý NL nếu INGREDIENT_VARIANCE
        if (adj.getAdjustmentType() == AdjustmentType.INGREDIENT_VARIANCE) {
            adjustIngredients(adj, dr);
        }

        adj.setApprovalStatus(ApprovalStatus.APPROVED);
        adj.setApprovedAt(Instant.now());
        adj.setApprovedBy(actorResolver.currentUserId());
        adj.setUpdatedAt(Instant.now());
        return adjustmentRepository.save(adj);
    }

    @Transactional
    public ProductionAdjustment reject(UUID adjustmentId, String reason) {
        ProductionAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionAdjustment", adjustmentId));

        if (adj.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Adjustment không ở trạng thái PENDING_APPROVAL.");
        }

        adj.setApprovalStatus(ApprovalStatus.REJECTED);
        adj.setRejectedReason(reason);
        adj.setUpdatedAt(Instant.now());
        return adjustmentRepository.save(adj);
    }

    // ── Private helpers ──────────────────────────────────────────

    private void adjustIngredients(ProductionAdjustment adj, DeliveryRecord dr) {
        ProductionRequestLine line = dr.getProductionRequestLine();
        Recipe recipe = resolveRecipe(line);
        if (recipe == null) return;

        Warehouse kitchen = warehouseRepository.findByCode(KITCHEN_CODE)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy kho KITCHEN"));

        List<RecipeLine> recipeLines = recipeLineRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getId());
        BigDecimal delta = adj.getDelta(); // dương = tăng sản lượng, âm = giảm

        for (RecipeLine rl : recipeLines) {
            BigDecimal nlDelta = rl.getQuantity().multiply(delta);

            if (nlDelta.compareTo(BigDecimal.ZERO) > 0) {
                // Tăng sản lượng → trừ thêm NL từ KITCHEN (FIFO)
                deductFromKitchen(nlDelta, rl.getItem().getId(), kitchen, adj.getId());
            } else if (nlDelta.compareTo(BigDecimal.ZERO) < 0) {
                // Giảm sản lượng → hoàn NL về KITCHEN (tạo lot mới)
                returnToKitchen(nlDelta.abs(), rl.getItem().getId(), kitchen, adj.getId());
            }
        }
    }

    private Recipe resolveRecipe(ProductionRequestLine line) {
        if (line.getRecipe() != null) return line.getRecipe();
        return recipeRepository.findByProductIdAndActiveTrue(line.getProduct().getId()).orElse(null);
    }

    private void deductFromKitchen(BigDecimal needed, UUID itemId, Warehouse kitchen, UUID refId) {
        List<StockLot> lots = stockLotRepository
                .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(itemId, BigDecimal.ZERO)
                .stream()
                .filter(l -> l.getWarehouse().getId().equals(kitchen.getId()))
                .toList();

        BigDecimal remaining = needed;
        for (StockLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal deduct = remaining.min(lot.getQtyRemaining());
            lot.setQtyRemaining(lot.getQtyRemaining().subtract(deduct));
            stockLotRepository.save(lot);

            StockMovement mv = new StockMovement();
            mv.setLot(lot);
            mv.setMovementType(MovementType.OUT);
            mv.setQty(deduct);
            mv.setRefId(refId);
            mv.setRefType(REF_TYPE);
            mv.setNote("Bù NL do điều chỉnh sản lượng tăng");
            stockMovementRepository.save(mv);

            remaining = remaining.subtract(deduct);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            Item item = entityManager.find(Item.class, itemId);
            String itemName = (item != null) ? item.getName() : itemId.toString();
            throw new IllegalStateException("Kho bếp không đủ NL để bù adjustment: " + itemName + " (thiếu " + remaining + ")");
        }
    }

    private void returnToKitchen(BigDecimal qty, UUID itemId, Warehouse kitchen, UUID refId) {
        // Lấy lot gần nhất của item này trong KITCHEN để lấy unitCost tham chiếu
        List<StockLot> existing = stockLotRepository
                .findByItemIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(itemId, BigDecimal.ZERO)
                .stream()
                .filter(l -> l.getWarehouse().getId().equals(kitchen.getId()))
                .toList();

        BigDecimal refCost = existing.isEmpty() ? BigDecimal.ZERO
                : existing.get(existing.size() - 1).getUnitCost();

        Item item = entityManager.find(Item.class, itemId);
        if (item == null) throw new ResourceNotFoundException("Item", itemId);

        StockLot returnLot = new StockLot();
        returnLot.setItem(item);
        returnLot.setWarehouse(kitchen);
        returnLot.setQtyInitial(qty);
        returnLot.setQtyRemaining(qty);
        returnLot.setUnitCost(refCost);
        returnLot.setReceivedDate(java.time.LocalDate.now());
        StockLot savedLot = stockLotRepository.save(returnLot);

        StockMovement mv = new StockMovement();
        mv.setLot(savedLot);
        mv.setMovementType(MovementType.IN);
        mv.setQty(qty);
        mv.setRefId(refId);
        mv.setRefType(REF_TYPE);
        mv.setNote("Hoàn NL do điều chỉnh sản lượng giảm");
        stockMovementRepository.save(mv);
    }
}
