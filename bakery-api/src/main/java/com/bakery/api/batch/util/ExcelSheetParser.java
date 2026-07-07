package com.bakery.api.batch.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Utility đọc file Excel (.xlsx) dùng Apache POI.
 *
 * Các vấn đề thực tế từ file:
 *  - Header nằm ở row 1-2 (không phải row 0)
 *  - Category rows xen kẽ (VD: "BÁNH MÌ NGỌT")
 *  - Merged cells (tên bánh ở nhiều dòng cùng 1 tên)
 *  - Ngày nằm trong header cell dạng "Sản Xuất/ Ngày 8/4"
 *  - Cột có thể là số, string, date, formula
 */
@Slf4j
public class ExcelSheetParser {

    private ExcelSheetParser() {}

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    /**
     * Mở workbook từ file path.
     * Caller chịu trách nhiệm đóng workbook sau khi dùng.
     */
    public static Workbook openWorkbook(String filePath) throws IOException {
        log.debug("Opening workbook: {}", filePath);
        try (FileInputStream fis = new FileInputStream(new File(filePath))) {
            return new XSSFWorkbook(fis);
        }
    }

    /**
     * Lấy tất cả data rows từ sheet, bỏ qua header và category rows.
     *
     * @param sheet       sheet cần đọc
     * @param dataStartRow  row index bắt đầu data thật (0-based). VD: 3 (sau 3 header rows)
     * @param productCodeCol  column index chứa Mã SP. Dòng không có mã SP sẽ bị skip.
     * @return List của map [columnIndex → cellValue]
     */
    public static List<Map<Integer, Object>> readDataRows(
            Sheet sheet,
            int dataStartRow,
            int productCodeCol) {

        List<Map<Integer, Object>> rows = new ArrayList<>();

        for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Lấy giá trị cột Mã SP
            Object codeVal = getCellValue(row.getCell(productCodeCol));
            if (codeVal == null || codeVal.toString().isBlank()) continue;

            // Chỉ lấy dòng có mã SP bắt đầu bằng "SP"
            if (!codeVal.toString().trim().toUpperCase().startsWith("SP")) continue;

            Map<Integer, Object> rowData = new LinkedHashMap<>();
            for (Cell cell : row) {
                rowData.put(cell.getColumnIndex(), getCellValue(cell));
            }
            rows.add(rowData);
        }

        log.debug("Sheet '{}': read {} data rows", sheet.getSheetName(), rows.size());
        return rows;
    }

    /**
     * Extract ngày từ header cell chứa text dạng "Sản Xuất/ Ngày 8/4".
     * Nếu cell là Date type thì lấy trực tiếp.
     * Nếu là String thì parse từ text.
     *
     * @param sheet       sheet cần đọc
     * @param headerRow   row index chứa ngày (0-based)
     * @param dateCol     column index chứa ngày
     * @param defaultYear năm mặc định nếu chỉ có ngày/tháng
     */
    public static Optional<LocalDate> extractDate(
            Sheet sheet,
            int headerRow,
            int dateCol,
            int defaultYear) {

        Row row = sheet.getRow(headerRow);
        if (row == null) return Optional.empty();

        Cell cell = row.getCell(dateCol);
        if (cell == null) return Optional.empty();

        // Case 1: Cell là kiểu Date (BaoCaoNgay)
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getDateCellValue()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
            return Optional.of(date);
        }

        // Case 2: Cell là String chứa "Ngày 8/4" hoặc "Sản Xuất/ Ngày 8/4"
        String text = getCellStringValue(cell);
        return parseDateFromText(text, defaultYear);
    }

    /**
     * Extract ngày từ text POS file dạng "Ngày lập: 09/04/2026 09:03"
     */
    public static Optional<LocalDate> extractDateFromPosHeader(Sheet sheet) {
        // Row 0: "Ngày lập: 09/04/2026 09:03"
        Row row = sheet.getRow(0);
        if (row == null) return Optional.empty();

        for (Cell cell : row) {
            String text = getCellStringValue(cell);
            if (text != null && text.contains("Ngày lập:")) {
                return parseDateFromPosText(text);
            }
        }
        return Optional.empty();
    }

    /**
     * Extract branch name từ POS file dạng "Chi nhánh: Chi nhánh Long Khanh"
     */
    public static Optional<String> extractBranchFromPosHeader(Sheet sheet) {
        // Row 3: "Chi nhánh: Chi nhánh Long Khanh"
        Row row = sheet.getRow(3);
        if (row == null) return Optional.empty();

        for (Cell cell : row) {
            String text = getCellStringValue(cell);
            if (text != null && text.contains("Chi nhánh:")) {
                String branch = text.replace("Chi nhánh:", "").trim();
                return Optional.of(branch);
            }
        }
        return Optional.empty();
    }

    /**
     * Lấy giá trị cell dạng String an toàn.
     */
    public static String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        Object val = getCellValue(cell);
        return val != null ? val.toString().trim() : null;
    }

    /**
     * Lấy giá trị cell dạng BigDecimal an toàn.
     */
    public static java.math.BigDecimal getCellDecimalValue(Cell cell) {
        if (cell == null) return null;
        Object val = getCellValue(cell);
        if (val == null) return null;
        try {
            return new java.math.BigDecimal(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------
    // Cell value extraction
    // -------------------------------------------------------

    public static Object getCellValue(Cell cell) {
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING  -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue();
                }
                // Trả về double, caller tự convert
                yield cell.getNumericCellValue();
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> evaluateFormula(cell);
            case BLANK   -> null;
            default      -> null;
        };
    }

    private static Object evaluateFormula(Cell cell) {
        try {
            // Đọc cached value của formula (không cần re-evaluate)
            return switch (cell.getCachedFormulaResultType()) {
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getDateCellValue();
                    }
                    yield cell.getNumericCellValue();
                }
                case STRING  -> {
                    String s = cell.getStringCellValue().trim();
                    yield s.isEmpty() ? null : s;
                }
                case BOOLEAN -> cell.getBooleanCellValue();
                case ERROR   -> null;
                default      -> null;
            };
        } catch (Exception e) {
            log.warn("Cannot evaluate formula at row={} col={}: {}",
                cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------
    // Date parsing
    // -------------------------------------------------------

    /**
     * Parse ngày từ text như "Sản Xuất/ Ngày 8/4" hoặc "Ngày 8/4".
     */
    static Optional<LocalDate> parseDateFromText(String text, int defaultYear) {
        if (text == null || text.isBlank()) return Optional.empty();

        try {
            // Tìm pattern d/M hoặc d/M/yyyy
            java.util.regex.Pattern p =
                java.util.regex.Pattern.compile("(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}))?");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                int day   = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year  = m.group(3) != null ? Integer.parseInt(m.group(3)) : defaultYear;
                return Optional.of(LocalDate.of(year, month, day));
            }
        } catch (Exception e) {
            log.warn("Cannot parse date from text: '{}'", text);
        }
        return Optional.empty();
    }

    /**
     * Parse ngày từ POS header "Ngày lập: 09/04/2026 09:03"
     */
    private static Optional<LocalDate> parseDateFromPosText(String text) {
        if (text == null) return Optional.empty();
        try {
            // Pattern dd/MM/yyyy
            java.util.regex.Pattern p =
                java.util.regex.Pattern.compile("(\\d{2})/(\\d{2})/(\\d{4})");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                int day   = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year  = Integer.parseInt(m.group(3));
                return Optional.of(LocalDate.of(year, month, day));
            }
        } catch (Exception e) {
            log.warn("Cannot parse POS date from: '{}'", text);
        }
        return Optional.empty();
    }
}
