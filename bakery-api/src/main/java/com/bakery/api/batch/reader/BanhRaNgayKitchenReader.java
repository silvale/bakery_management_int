package com.bakery.api.batch.reader;

import com.bakery.api.batch.dto.KitchenOutputRow;
import com.bakery.api.batch.util.ErrorRowCollector;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc file BanhRaNgay đã được bếp điền thực tế.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  FILE STRUCTURE (mỗi sheet)                                │
 * ├─────────────────────────────────────────────────────────────┤
 * │  Row 0: "BÁN HÀNG" (title)                                 │
 * │  Row 1: STT | Mã SP | BÁNH | Sản Xuất/Ngày X/Y (merged)   │
 * │  Row 2: (trống) | (trống) | (trống) | dự kiến | thực tế   │
 * │          | tồn tối | hủy                                   │
 * │  Row 3+: Data rows                                          │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ┌──────┬────────────────────┬──────────────────────────────────┐
 * │ Col  │ Nội dung           │ Ghi chú                          │
 * ├──────┼────────────────────┼──────────────────────────────────┤
 * │  0   │ STT                │ bỏ qua                           │
 * │  1   │ Mã SP (IN_CODE)    │ BMN-, BL-, COO-, PK-, PL-       │
 * │  2   │ Tên bánh           │                                  │
 * │  3   │ Dự kiến            │ số lượng kế hoạch               │
 * │  4   │ Thực tế            │ số | "x"/"X" = bằng dự kiến     │
 * │  5   │ Tồn tối            │ (chưa dùng)                     │
 * │  6   │ Hủy                │ (chưa dùng)                     │
 * └──────┴────────────────────┴──────────────────────────────────┘
 */
@Slf4j
public class BanhRaNgayKitchenReader {

    private static final int DATA_START_ROW = 3; // Row 4 (0-based)
    private static final int COL_MA_SP      = 1;
    private static final int COL_TEN_BANH   = 2;
    private static final int COL_DU_KIEN    = 3;
    private static final int COL_THUC_TE    = 4;

    /**
     * Đọc tất cả sheets trong workbook, trả về danh sách hàng có thực tế > 0.
     */
    public static List<KitchenOutputRow> read(
            String filePath,
            ErrorRowCollector collector,
            LocalDate productionDate) throws IOException {

        List<KitchenOutputRow> result = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(new java.io.FileInputStream(filePath))) {

            int sheetCount = wb.getNumberOfSheets();
            log.info("BanhRaNgayKitchenReader | Ngày: {} | {} sheets", productionDate, sheetCount);

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) continue;

                String sheetName = sheet.getSheetName();
                List<KitchenOutputRow> sheetRows =
                    readSheet(sheet, sheetName, productionDate, collector);

                log.info("  Sheet '{}': {} dòng có thực tế", sheetName, sheetRows.size());
                result.addAll(sheetRows);
            }
        }

        log.info("BanhRaNgayKitchenReader: tổng {} dòng", result.size());
        return result;
    }

    // ── Sheet reader ──────────────────────────────────────────

    private static List<KitchenOutputRow> readSheet(
            Sheet sheet,
            String sheetName,
            LocalDate productionDate,
            ErrorRowCollector collector) {

        List<KitchenOutputRow> rows = new ArrayList<>();

        for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            collector.incrementTotal();

            try {
                // Mã SP — bỏ qua dòng trống hoặc không phải IN_CODE
                String masp = getStringValue(row.getCell(COL_MA_SP));
                if (masp == null || masp.isBlank()) continue;
                if (!isValidInCode(masp)) continue;

                // Dự kiến
                BigDecimal duKien = getDecimalValue(row.getCell(COL_DU_KIEN));
                if (duKien == null) duKien = BigDecimal.ZERO;

                // Thực tế — "x" → dùng dự kiến; 0/null → skip
                BigDecimal thucTe = resolveThucTe(row.getCell(COL_THUC_TE), duKien);
                if (thucTe == null || thucTe.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("  Sheet {} Row {}: {} thực tế=0 → bỏ qua", sheetName, i + 1, masp);
                    continue;
                }

                String tenBanh = getStringValue(row.getCell(COL_TEN_BANH));

                rows.add(KitchenOutputRow.builder()
                    .productionDate(productionDate)
                    .productCode(masp.trim().toUpperCase())
                    .productName(tenBanh)
                    .qtyPlanned(duKien)
                    .qtyActual(thucTe)
                    .sheetName(sheetName)
                    .rowIndex(i)
                    .build());

                collector.incrementSuccess();

            } catch (Exception e) {
                log.error("Lỗi đọc row {}/{}: {}", sheetName, i, e.getMessage());
                collector.addError(i, "unknown", e.getMessage());
            }
        }

        return rows;
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Resolve cột "thực tế":
     *   - "x" / "X"   → trả về dự kiến
     *   - số > 0       → trả về số đó
     *   - 0 / null     → trả về null (caller sẽ skip)
     */
    private static BigDecimal resolveThucTe(Cell cell, BigDecimal duKien) {
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if ("x".equalsIgnoreCase(s)) {
                return duKien; // x = thực tế bằng dự kiến
            }
            // chuỗi khác (trống, gạch, ...) → skip
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            BigDecimal val = BigDecimal.valueOf(cell.getNumericCellValue());
            return val.compareTo(BigDecimal.ZERO) > 0 ? val : null;
        }

        return null;
    }

    /** IN_CODE hợp lệ: bắt đầu bằng BMN-, BMM-, BL-, COO-, PK-, PL- */
    private static boolean isValidInCode(String code) {
        if (code == null) return false;
        String upper = code.toUpperCase();
        return upper.startsWith("BMN-") || upper.startsWith("BMM-")
            || upper.startsWith("BL-")  || upper.startsWith("COO-")
            || upper.startsWith("PK-")  || upper.startsWith("PL-");
    }

    private static String getStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    private static BigDecimal getDecimalValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> {
                try { yield new BigDecimal(cell.getStringCellValue().trim()); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> null;
        };
    }
}
