package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bakery.api.production.entity.DeliveryRecord;
import com.bakery.api.production.entity.ProductionAdjustment;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.framework.entity.AdjustmentSource;
import com.bakery.framework.entity.AdjustmentType;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.metadata.ReferenceValue;

/**
 * Điều chỉnh sản lượng. Trả DTO thay entity: entity có quan hệ LAZY {@code deliveryRecord} nên trả thẳng sẽ 500
 * {@code LazyInitializationException} (open-in-view tắt). Phải dựng trong transaction của service.
 */
public record ProductionAdjustmentResponse(
        UUID id,
        UUID deliveryRecordId,
        DeliveryRef deliveryRecord,
        RequestRef productionRequest,
        ReferenceValue product,
        AdjustmentType adjustmentType,
        AdjustmentSource source,
        BigDecimal originalQty,
        BigDecimal adjustedQty,
        BigDecimal delta,
        String reason,
        ApprovalStatus approvalStatus,
        Instant approvedAt,
        String approvedBy,
        String rejectedReason,
        Instant createdAt,
        String createdBy,
        Instant updatedAt) {

    public record DeliveryRef(UUID id) {}

    public record RequestRef(UUID id, String code) {}

    public static ProductionAdjustmentResponse from(ProductionAdjustment a) {
        DeliveryRecord dr = a.getDeliveryRecord();
        ProductionRequestLine line = dr != null ? dr.getProductionRequestLine() : null;
        ProductionRequest pr = line != null ? line.getProductionRequest() : null;
        return new ProductionAdjustmentResponse(a.getId(),
                dr != null ? dr.getId() : null, dr != null ? new DeliveryRef(dr.getId()) : null,
                pr != null ? new RequestRef(pr.getId(), pr.getCode()) : null,
                line != null && line.getProduct() != null ? new ReferenceValue(line.getProduct().getCode(), line.getProduct().getName()) : null,
                a.getAdjustmentType(), a.getSource(), a.getOriginalQty(), a.getAdjustedQty(), a.getDelta(), a.getReason(),
                a.getApprovalStatus(), a.getApprovedAt(), a.getApprovedBy(), a.getRejectedReason(),
                a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt());
    }
}
