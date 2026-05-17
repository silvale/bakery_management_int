package com.bakery.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw data đọc từ file BanhRaNgay.xlsx (Admin → Bếp).
 * 1 dòng = 1 sản phẩm với số lượng yêu cầu.
 */
@Getter
@Builder
public class ProductionRequestRow {
    private LocalDate   orderDate;       // parse từ header "Sản Xuất/ Ngày 8/4"
    private String      productCode;     // Mã SP (VD: SP022575)
    private String      productName;     // Tên bánh
    private BigDecimal  qtyRequested;    // Dự kiến (cột E)
    private String      unit;            // PCS mặc định, KG nếu là Lan
    private String      sheetName;       // tên sheet: B.MI, LAN
    private int         rowIndex;        // row trong Excel (để log lỗi)
}
