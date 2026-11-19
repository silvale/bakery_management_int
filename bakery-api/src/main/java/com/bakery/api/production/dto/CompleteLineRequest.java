/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.entity.AdjustmentType;

/**
 * Payload cho 1 line trong batch complete.
 *
 * @param lineId         ID của ProductionRequestLine
 * @param qtyProduced    Số lượng thực tế bếp sản xuất
 * @param adjustmentType Bắt buộc nếu qtyProduced ≠ plannedQty: INGREDIENT_VARIANCE | PRODUCTION_WASTAGE
 * @param reason         Lý do điều chỉnh (bắt buộc khi có adjustmentType)
 * @param note           Ghi chú tùy chọn
 * @param designs        Số bánh theo mẫu — bắt buộc khi thành phẩm có mẫu đang dùng và qtyProduced > 0
 */
public record CompleteLineRequest(
        UUID lineId,
        BigDecimal qtyProduced,
        AdjustmentType adjustmentType,
        String reason,
        String note,
        /** Số bánh theo mẫu (chỉ bánh kem trong phiếu DAILY); tổng phải bằng qtyProduced. */
        java.util.List<com.bakery.api.cake.dto.DesignQtyRequest> designs) {}
