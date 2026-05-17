package com.bakery.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw data đọc từ BigProductBySaleByCat.xlsx (POS Export).
 *
 * File structure:
 *   Row 0: "Ngày lập: 09/04/2026 09:03"
 *   Row 3: "Chi nhánh: Chi nhánh Long Khanh"
 *   Row 5: Header (Mã hàng, Tên hàng, SL bán, Doanh thu...)
 *   Row 6: Summary — SKIP
 *   Row 7+: Data rows
 *
 * Columns (0-based):
 *   B(1): Mã hàng     → productCode
 *   C(2): Tên hàng    → productName
 *   F(5): SL bán      → qtySold
 *   G(6): Doanh thu   → revenue
 *   H(7): SL trả      → qtyReturned (để tham khảo)
 *   K(10): DT thuần   → netRevenue
 *
 * Lưu ý: Mã SP có thể xuất hiện nhiều lần → Batch phải SUM.
 */
@Getter
@Builder
public class PosTransactionRow {
    private LocalDate   transactionDate;
    private String      branchName;       // parse từ header "Chi nhánh: ..."
    private String      productCode;      // Mã hàng
    private String      productName;      // Tên hàng
    private BigDecimal  qtySold;          // SL bán
    private BigDecimal  revenue;          // Doanh thu
    private BigDecimal  qtyReturned;      // SL trả
    private BigDecimal  netRevenue;       // Doanh thu thuần
    private int         rowIndex;
}
