package com.bakery.api.batch.reader;

import com.bakery.api.batch.dto.DailyInventoryRow;
import com.bakery.api.batch.util.ExcelSheetParser;
import com.bakery.api.batch.util.ErrorRowCollector;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Đọc file BaoCaoNgay.xlsx — Nhân viên tổng hợp kiểm kê cuối ngày.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  COLUMN MAPPING — thay đổi ở đây nếu file thay đổi cột    │
 * ├──────┬──────────────────────┬──────────────────────────────┤
 * │ Col  │ Tên cột trong file   │ Mapping vào DTO              │
 * ├──────┼──────────────────────┼──────────────────────────────┤
 * │  1   │ Mã SP                │ productCode                  │
 * │  2   │ Tên bánh             │ productName                  │
 * │  3   │ Tồn hôm trước        │ qtyOpening                   │
 * │  4   │ Bánh sáng (từ bếp)   │ qtyReceived                  │
 * │  5   │ Tồn tối (còn lại)    │ qtyClosing                   │
 * │  6   │ Giảm giá             │ (bỏ qua)                     │
 * │  7   │ Tổng bán             │ (bỏ qua tạm thời)            │
 * │  8   │ Hủy                  │ qtyCancelled                 │
 * └──────┴──────────────────────┴──────────────────────────────┘
 *
 * qty_sold_reported KHÔNG đọc từ file.
 * Tính tự động: D + E - F = qtyOpening + qtyReceived - qtyClosing
 * └──────┴──────────────────────┴──────────────────────────────┘
 *
 * Sheet config:
 *   ┌──────┬──────────────┐
 *   │ Sheet│ dataStartRow │
 *   ├──────┼──────────────┤
 *   │ SX   │ 2            │
 *   │ Lan  │ 2            │
 *   └──────┴──────────────┘
 */
@Slf4j
public class DailyInventoryReader {

    // ── Sheet config ─────────────────────────────────────────
    private static final List<SheetConfig> SHEETS = List.of(
            new SheetConfig("B.MI", 2),
            new SheetConfig("LAN", 2)
    );

    // ── Column mapping — SỬA ĐÂY nếu file thay đổi cột ──────
    private static final int COL_SP_CODE       = 1;  // Mã SP
    private static final int COL_NAME          = 2;  // Tên bánh
    private static final int COL_OPENING       = 3;  // Tồn hôm trước
    private static final int COL_RECEIVED      = 4;  // Bánh sáng (nhận từ bếp)
    private static final int COL_CLOSING       = 5;  // Tồn tối (còn lại cuối ngày)
    // COL 6: Giảm giá — bỏ qua
    // COL 7: Tổng bán — bỏ qua tạm thời, sẽ có logic riêng sau
    private static final int COL_CANCELLED     = 8;  // Hủy

    public static List<DailyInventoryRow> read(
            String filePath,
            ErrorRowCollector collector,
            LocalDate processDate) throws IOException {

        List<DailyInventoryRow> result = new ArrayList<>();

        try (Workbook wb = ExcelSheetParser.openWorkbook(filePath)) {
            for (SheetConfig cfg : SHEETS) {
                Sheet sheet = wb.getSheet(cfg.sheetName());
                if (sheet == null) {
                    log.warn("Sheet '{}' không tìm thấy trong: {}", cfg.sheetName(), filePath);
                    continue;
                }

                log.info("Đọc sheet '{}' | Ngày: {}", cfg.sheetName(), processDate);

                List<Map<Integer, Object>> rows = ExcelSheetParser.readDataRows(
                        sheet, cfg.dataStartRow(), COL_SP_CODE
                );

                for (Map<Integer, Object> row : rows) {
                    collector.incrementTotal();
                    try {
                        String productCode = safeString(row.get(COL_SP_CODE));
                        if (productCode == null) {
                            collector.addError(0, "Ma_SP", "Mã SP trống");
                            continue;
                        }

                        BigDecimal qtyOpening  = nullSafe(safeDecimal(row.get(COL_OPENING)));
                        BigDecimal qtyReceived = nullSafe(safeDecimal(row.get(COL_RECEIVED)));
                        BigDecimal qtyClosing  = nullSafe(safeDecimal(row.get(COL_CLOSING)));

                        // qty_sold_reported = D + E - F (tính tự động, không đọc từ cột H)
                        BigDecimal qtySoldReported = qtyOpening.add(qtyReceived).subtract(qtyClosing);

                        result.add(DailyInventoryRow.builder()
                                .inventoryDate(processDate)
                                .productCode(productCode.trim().toUpperCase())
                                .productName(safeString(row.get(COL_NAME)))
                                .qtyOpening(qtyOpening)
                                .qtyReceived(qtyReceived)
                                .qtyClosing(qtyClosing)
                                .qtySoldReported(qtySoldReported)
                                .qtyCancelled(nullSafe(safeDecimal(row.get(COL_CANCELLED))))
                                .sheetName(cfg.sheetName())
                                .rowIndex(0)
                                .build());

                        collector.incrementSuccess();

                    } catch (Exception e) {
                        log.error("Lỗi đọc row BaoCaoNgay sheet {}: {}", cfg.sheetName(), e.getMessage());
                        collector.addError(0, "unknown", e.getMessage());
                    }
                }
            }
        }

        log.info("DailyInventoryReader: {} dòng hợp lệ", result.size());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────

    private static String safeString(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal safeDecimal(Object val) {
        if (val == null) return null;
        try {
            if (val instanceof Double d) return BigDecimal.valueOf(d);
            return new BigDecimal(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal nullSafe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    // ── Sheet config record ───────────────────────────────────
    private record SheetConfig(String sheetName, int dataStartRow) {}
}