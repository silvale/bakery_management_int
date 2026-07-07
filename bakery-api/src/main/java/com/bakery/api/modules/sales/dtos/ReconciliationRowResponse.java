package com.bakery.api.modules.sales.dtos;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Kết quả 3-way reconciliation cho 1 sản phẩm / 1 ngày / 1 chi nhánh.
 *
 * status:
 *   OK           — variance = 0 và đủ 3 nguồn dữ liệu
 *   DISCREPANCY  — variance != 0
 *   MISSING_DATA — thiếu 1 hoặc nhiều nguồn (BEP=0 hoặc POS=0 hoặc SHOP=0)
 */
@Data
@Builder
public class ReconciliationRowResponse {

    private UUID      itemId;
    private String    itemCode;
    private String    itemName;
    private LocalDate reconDate;
    private UUID      branchId;
    private String    branchName;

    /** Số lượng Bếp giao sang Shop (TRANSFER ACTIVE) */
    private BigDecimal qtyBepDelivered;

    /** Số lượng POS ghi nhận đã bán */
    private BigDecimal qtyPosSold;

    /** Số lượng Shop báo cáo đã hủy */
    private BigDecimal qtyDestroyed;

    /**
     * variance = (qty_pos_sold + qty_destroyed) - qty_bep_delivered
     * Dương → Shop tiêu thụ nhiều hơn Bếp giao
     * Âm   → Bếp giao nhiều hơn Shop ghi nhận
     */
    private BigDecimal variance;

    /** OK | DISCREPANCY | MISSING_DATA */
    private String status;

    /** Ghi chú tự động — mô tả cụ thể vấn đề nếu status != OK */
    private String note;
}
