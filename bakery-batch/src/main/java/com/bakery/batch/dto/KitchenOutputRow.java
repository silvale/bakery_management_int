package com.bakery.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1 dòng từ file BanhRaNgay (bếp đã điền thực tế).
 *
 * Quy tắc cột "thực tế":
 *   - Số > 0   → dùng số đó
 *   - "x"/"X"  → thực tế = dự kiến
 *   - 0 / null → bếp không sản xuất → skip
 */
@Getter
@Builder
public class KitchenOutputRow {
    private LocalDate  productionDate; // từ API param ?date=
    private String     productCode;   // Mã SP — đã là IN_CODE (BMN-, BL-, COO-, PK-, PL-)
    private String     productName;   // Tên bánh
    private BigDecimal qtyPlanned;    // Dự kiến
    private BigDecimal qtyActual;     // Thực tế (đã resolve "x" → dự kiến)
    private String     sheetName;     // tên sheet (để log)
    private int        rowIndex;      // row trong Excel (để log lỗi)
}
