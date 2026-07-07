package com.bakery.api.modules.sales.dtos;

import com.bakery.api.framework.enums.ItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request submit báo cáo cuối ngày của nhân viên Shop.
 *
 * Nhân viên nhập:
 *   - qty_leftover_theoretical: tồn sổ sách (hệ thống tính = nhập từ bếp - bán POS)
 *   - qty_destroyed_actual: số thực tế đã hủy (đếm tay)
 *
 * Chênh lệch (qty_leftover_theoretical - qty_destroyed_actual) là mất mát không rõ lý do.
 */
public record DailyShopReportRequest(

        @NotNull LocalDate reportDate,

        @NotNull UUID branchId,

        /** item_id của sản phẩm (product.id) */
        @NotNull UUID itemId,

        ItemType itemType,

        /** Tồn sổ sách lý thuyết tại thời điểm submit */
        @NotNull
        @DecimalMin("0")
        BigDecimal qtyLeftoverTheoretical,

        /** Số bánh thực tế nhân viên đã hủy */
        @NotNull
        @DecimalMin("0")
        BigDecimal qtyDestroyedActual,

        String note
) {}
