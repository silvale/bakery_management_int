package com.bakery.api.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một dòng trong file Excel tồn kho ban đầu (TonKhoBanDau).
 *
 * Format file:
 *   Row 0: Tiêu đề (merge)
 *   Row 1: Header: STT | EX_CODE | Tên SP | SL tồn | Ngày SX | Ngày HSD
 *   Row 2+: Data
 *
 * Ngày SX / Ngày HSD là optional — nếu trống sẽ dùng ngày nhập hoặc tính từ shelfDays.
 */
@Getter
@Builder
public class OpeningStockRow {

    /** EX_CODE từ máy POS — sẽ map sang IN_CODE qua product_mapping */
    private String    exCode;

    /** Tên sản phẩm (chỉ để log, không dùng để map) */
    private String    productName;

    /** Số lượng tồn kho hiện tại */
    private BigDecimal qty;

    /** Ngày sản xuất — null nếu không điền (sẽ dùng processDate) */
    private LocalDate productionDate;

    /** Ngày hết hạn — null nếu không điền (sẽ tính từ shelfDays) */
    private LocalDate expiryDate;

    /** Dòng trong file (0-indexed) — dùng cho error reporting */
    private int rowIndex;
}
