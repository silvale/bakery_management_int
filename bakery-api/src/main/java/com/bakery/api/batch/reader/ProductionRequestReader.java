package com.bakery.api.batch.reader;

import com.bakery.api.batch.dto.ProductionRequestRow;
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
 * Đọc file BanhRaNgay.xlsx — Admin tạo, thể hiện YÊU CẦU sản xuất.
 *
 * Chỉ quan tâm cột DỰ KIẾN (col 4) → qtyRequested.
 * Không đọc cột thực tế (cột đó thuộc về XuatRa).
 *
 * Sheet config:
 *   ┌─────────────┬──────────────┬───────────────────┐
 *   │ Sheet       │ dataStartRow │ unit              │
 *   ├─────────────┼──────────────┼───────────────────┤
 *   │ B.MÌ        │ 3            │ PCS               │
 *   │ LAN         │ 2            │ KG                │
 *   └─────────────┴──────────────┴───────────────────┘
 */
@Slf4j
public class ProductionRequestReader {

    // ── Sheet config ─────────────────────────────────────────
    private static final List<SheetConfig> SHEETS = List.of(
        new SheetConfig("B.MI", 3, "PCS"),
        new SheetConfig("LAN",  2, "KG")
    );

    // ── Column mapping ────────────────────────────────────────
    private static final int COL_SP_CODE    = 1;  // Mã SP
    private static final int COL_NAME       = 2;  // Tên bánh
    private static final int COL_REQUESTED  = 4;  // Dự kiến ← file BanhRaNgay

    public static List<ProductionRequestRow> read(
            String filePath,
            ErrorRowCollector collector,
            LocalDate processDate) throws IOException {

        List<ProductionRequestRow> result = new ArrayList<>();

        try (Workbook wb = ExcelSheetParser.openWorkbook(filePath)) {
            for (SheetConfig cfg : SHEETS) {
                Sheet sheet = wb.getSheet(cfg.sheetName());
                if (sheet == null) {
                    log.warn("Sheet '{}' không tìm thấy trong: {}", cfg.sheetName(), filePath);
                    continue;
                }

                log.info("Đọc sheet '{}' | Ngày: {} | Unit: {}", cfg.sheetName(), processDate, cfg.unit());

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

                        BigDecimal qtyRequested = safeDecimal(row.get(COL_REQUESTED));
                        if (isZeroOrNull(qtyRequested)) {
                            log.debug("Bỏ qua {} - dự kiến = 0", productCode);
                            continue;
                        }

                        result.add(ProductionRequestRow.builder()
                            .orderDate(processDate)
                            .productCode(productCode.trim().toUpperCase())
                            .productName(safeString(row.get(COL_NAME)))
                            .qtyRequested(qtyRequested)
                            .unit(cfg.unit())
                            .sheetName(cfg.sheetName())
                            .rowIndex(0)
                            .build());

                        collector.incrementSuccess();

                    } catch (Exception e) {
                        log.error("Lỗi đọc row BanhRaNgay: {}", e.getMessage());
                        collector.addError(0, "unknown", e.getMessage());
                    }
                }
            }
        }

        log.info("ProductionRequestReader: {} dòng hợp lệ", result.size());
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

    private static boolean isZeroOrNull(BigDecimal val) {
        return val == null || val.compareTo(BigDecimal.ZERO) <= 0;
    }

    // ── Sheet config record ───────────────────────────────────
    private record SheetConfig(String sheetName, int dataStartRow, String unit) {}
}
