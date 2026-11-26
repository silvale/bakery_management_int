package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.service.ProductionZone;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import com.bakery.api.production.dto.ProductionAdjustmentResponse;
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

    private static final String REF_TYPE = "PRODUCTION_ADJUSTMENT";

    private final ProductionAdjustmentRepository adjustmentRepository;
    private final DeliveryRecordRepository deliveryRecordRepository;
    private final BakeryActorResolver actorResolver;

    // ── Queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductionAdjustmentResponse> findByDeliveryRecord(UUID deliveryRecordId) {
        return adjustmentRepository.findByDeliveryRecordId(deliveryRecordId).stream().map(ProductionAdjustmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionAdjustmentResponse> findPending() {
        return adjustmentRepository.findByApprovalStatus(ApprovalStatus.PENDING_APPROVAL).stream().map(ProductionAdjustmentResponse::from).toList();
    }

    // ── Admin tạo correction ─────────────────────────────────────

    /**
     * Admin tạo điều chỉnh sau khi bếp đã submit.
     * Không giới hạn số lần (khác với KITCHEN_COMPLETE chỉ 1 lần).
     */
    @Transactional
    public ProductionAdjustmentResponse createAdminCorrection(UUID deliveryRecordId,
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
        return ProductionAdjustmentResponse.from(adjustmentRepository.save(adj));
    }

    // ── Bếp trưởng duyệt ────────────────────────────────────────

    /**
     * Bếp trưởng duyệt adjustment.
     *
     * <p>Cả INGREDIENT_VARIANCE lẫn PRODUCTION_WASTAGE chỉ cập nhật số lượng, không động tồn NL (24/§5.5).
     */
    @Transactional
    public ProductionAdjustmentResponse approve(UUID adjustmentId) {
        ProductionAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionAdjustment", adjustmentId));

        if (adj.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Adjustment không ở trạng thái PENDING_APPROVAL.");
        }

        // Cập nhật DeliveryRecord.qtyProduced
        DeliveryRecord dr = adj.getDeliveryRecord();
        dr.setQtyProduced(adj.getAdjustedQty());
        deliveryRecordRepository.save(dr);

        // 24/§5.5: hoàn thành đã trừ bảng tạm theo số làm thật; duyệt điều chỉnh KHÔNG động tồn NL nữa (hết lỗi trừ hai lần).
        // Phần NL thừa/thiếu do lệch nằm ở bảng tạm và lộ ra lúc đóng phiên.

        adj.setApprovalStatus(ApprovalStatus.APPROVED);
        adj.setApprovedAt(Instant.now());
        adj.setApprovedBy(actorResolver.currentUserId());
        adj.setUpdatedAt(Instant.now());
        return ProductionAdjustmentResponse.from(adjustmentRepository.save(adj));
    }

    @Transactional
    public ProductionAdjustmentResponse reject(UUID adjustmentId, String reason) {
        ProductionAdjustment adj = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionAdjustment", adjustmentId));

        if (adj.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Adjustment không ở trạng thái PENDING_APPROVAL.");
        }

        adj.setApprovalStatus(ApprovalStatus.REJECTED);
        adj.setRejectedReason(reason);
        adj.setUpdatedAt(Instant.now());
        return ProductionAdjustmentResponse.from(adjustmentRepository.save(adj));
    }
}
