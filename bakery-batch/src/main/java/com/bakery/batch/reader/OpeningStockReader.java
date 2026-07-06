package com.bakery.batch.reader;

import com.bakery.batch.dto.OpeningStockRow;
import com.bakery.batch.util.ErrorRowCollector;
import com.bakery.batch.util.ExcelSheetParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc file Excel tồn kho ban đầu (TonKhoBanDau).
 *
 * Format file:
 *   Row 0: Tiêu đề (có thể merge)
 *   Row 1: Header — STT | EX_CODE | Tên SP | SL tồn | Ngày SX | Ngày HSD
 *   Row 2+: Data
 *
 * Cột:
 *   0 — STT        (bỏ qua)
 *   1 — EX_CODE    (bắt buộc)
 *   2 — Tên SP     (optional, chỉ để log)
 *   3 — SL tồn     (bắt buộc, > 0)
 *   4 — Ngày SX    (optional, dd/MM/yyyy hoặc dd/MM)
 *   5 — Ngày HSD   (optional, dd/MM/yyyy hoặc dd/MM)
 *
 * Dòng bị bỏ qua: EX_CODE trống, SL ≤ 0, hoặc không phải số.
 */
@Slf4j
public class OpeningStockReader {

    private OpeningStockReader() {}

    // Cột
    private static final int COL_STT            = 0;
    private static final int COL_EX_CODE        = 1;
    private static final int COL_TEN_SP         = 2;
    private static final int COL_SL_TON         = 3;
    private static final int COL_NGAY_SX        = 4;
    private static final int COL_NGAY_HSD       = 5;

    private static final int DATA_START_ROW     = 2; // Row 0=title, Row 1=header

    private static final DateTimeFormatter FMT_FULL  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * Đọc file Excel và trả về danh sách các dòng hợp lệ.
     *
     * @param filePath       đường dẫn file
     * @param collector      thu thập lỗi từng dòng
     * @param defaultYear    năm mặc định khi Ngày SX/HSD chỉ có dd/MM
     * @return danh sách OpeningStockRow (chỉ dòng có SL > 0)
     */
    public static List<OpeningStockRow> read(
            String filePath,
            ErrorRowCollector collector,
            int defaultYear) throws Exception {

        List<OpeningStockRow> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {

            // Đọc sheet đầu tiên
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                log.warn("File không có sheet nào: {}", filePath);
                return result;
            }

            log.info("OpeningStockReader | sheet='{}' | lastRow={}",
                sheet.getSheetName(), sheet.getLastRowNum());

            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // EX_CODE
                String exCode = ExcelSheetParser.getCellStringValue(row.getCell(COL_EX_CODE));
                if (exCode == null || exCode.isBlank()) continue;
                exCode = exCode.trim().toUpperCase();

                // Dòng "Tổng" hoặc ghi chú — bỏ qua
                if (exCode.startsWith("TỔNG") || exCode.startsWith("TONG")
                        || exCode.startsWith("*") || exCode.startsWith("GHI CHÚ")) continue;

                // Tên SP
                String tenSp = ExcelSheetParser.getCellStringValue(row.getCell(COL_TEN_SP));

                // SL tồn
                BigDecimal qty = ExcelSheetParser.getCellDecimalValue(row.getCell(COL_SL_TON));
                if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("  Row {}: EX_CODE={} SL=0/null → bỏ qua", i, exCode);
                    continue;
                }

                // Ngày SX (optional)
                LocalDate productionDate = parseDate(row.getCell(COL_NGAY_SX), defaultYear);

                // Ngày HSD (optional)
                LocalDate expiryDate = parseDate(row.getCell(COL_NGAY_HSD), defaultYear);

                result.add(OpeningStockRow.builder()
                    .exCode(exCode)
                    .productName(tenSp)
                    .qty(qty)
                    .productionDate(productionDate)
                    .expiryDate(expiryDate)
                    .rowIndex(i)
                    .build());

                log.debug("  Row {}: exCode={} qty={} sx={} hsd={}",
                    i, exCode, qty, productionDate, expiryDate);
            }
        }

        log.info("OpeningStockReader: {} dòng hợp lệ", result.size());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Parse ngày từ cell — hỗ trợ:
     *   - Cell kiểu Date (Excel numeric date)
     *   - String "dd/MM/yyyy" hoặc "dd/MM"
     *   - Trả về null nếu trống hoặc không parse được
     */
    private static LocalDate parseDate(Cell cell, int defaultYear) {
        if (cell == null) return null;

        // Excel date cell
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                return cell.getDateCellValue()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            } catch (Exception e) {
                return null;
            }
        }

        // String cell
        String text = ExcelSheetParser.getCellStringValue(cell);
        if (text == null || text.isBlank()) return null;

        // Thử dd/MM/yyyy
        try {
            return LocalDate.parse(text.trim(), FMT_FULL);
        } catch (DateTimeParseException ignored) {}

        // Thử dd/MM → gắn thêm năm mặc định
        try {
            return LocalDate.parse(text.trim() + "/" + defaultYear, FMT_FULL);
        } catch (DateTimeParseException ignored) {}

        log.debug("Không parse được ngày: '{}'", text);
        return null;
    }
}
