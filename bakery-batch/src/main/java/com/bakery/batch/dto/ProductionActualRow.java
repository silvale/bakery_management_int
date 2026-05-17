package com.bakery.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw data đọc từ file XuatRa.xlsx (Bếp → xuất thực tế).
 *
 * Chỉ quan tâm số thực tế bếp làm ra.
 * qtyRequested không đọc ở đây — thuộc về BanhRaNgay.
 */
@Getter
@Builder
public class ProductionActualRow {
    private LocalDate   orderDate;
    private String      productCode;
    private String      productName;
    private BigDecimal  qtyActual;      // Thực tế bếp làm (col 5)
    private BigDecimal  qtyCancelled;   // Hủy (col 8)
    private String      unit;
    private String      sheetName;
    private int         rowIndex;
}
