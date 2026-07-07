package com.bakery.api.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Raw data đọc từ file BaoCaoNgay.xlsx — sheet SX và Lan.
 *
 * Columns:
 *   B: Mã SP
 *   C: Tên bánh
 *   D: Tồn hôm trước  → qtyOpening
 *   E: Bánh sáng       → qtyReceived (từ bếp)
 *   F: Tồn tối         → qtyClosing
 *   G: Giảm giá        → ignored (chưa dùng)
 *   H: Tổng bán        → qtySoldReported (do nhân viên điền, để đối chiếu)
 *   I: Hủy             → qtyCancelled
 */
@Getter
@Builder
public class DailyInventoryRow {
    private LocalDate   inventoryDate;
    private String      productCode;
    private String      productName;
    private BigDecimal  qtyOpening;       // Tồn hôm trước
    private BigDecimal  qtyReceived;      // Bánh sáng (nhận từ bếp)
    private BigDecimal  qtyClosing;       // Tồn tối (còn lại cuối ngày)
    private BigDecimal  qtyCancelled;     // Hủy
    private BigDecimal  qtySoldReported;  // Tổng bán (nhân viên ghi — để tham khảo)
    private String      sheetName;
    private int         rowIndex;
}
