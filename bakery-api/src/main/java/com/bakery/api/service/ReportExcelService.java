package com.bakery.api.service;

import com.bakery.common.entity.DailyReconcile;
import com.bakery.common.entity.enums.ReconcileStatus;
import com.bakery.common.repository.BranchRepository;
import com.bakery.common.repository.DailyReconcileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExcelService {

    private final DailyReconcileRepository dailyReconcileRepository;
    private final BranchRepository         branchRepository;

    // ── Colors ────────────────────────────────────────────────
    private static final String COLOR_HEADER_BG  = "2F5496"; // Xanh đậm
    private static final String COLOR_HEADER_FG  = "FFFFFF"; // Trắng
    private static final String COLOR_ERROR_FG   = "FF0000"; // Đỏ chữ
    private static final String COLOR_ERROR_BG   = "FFE0E0"; // Đỏ nhạt nền
    private static final String COLOR_OK_BG      = "E8F5E9"; // Xanh lá nhạt
    private static final String COLOR_ALT_ROW    = "F5F5F5"; // Xám nhạt xen kẽ

    @Transactional(readOnly = true)
    public byte[] exportDailyReport(LocalDate date) throws IOException {

        var shopBranch = branchRepository.findAll().stream()
            .filter(b -> !b.getIsMain())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy cửa hàng"));

        List<DailyReconcile> records = dailyReconcileRepository
            .findAllByBranchIdAndReconDate(shopBranch.getId(), date);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Báo Cáo Ngày " +
                date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

            // ── Styles ────────────────────────────────────────
            CellStyle headerStyle  = createHeaderStyle(wb);
            CellStyle normalStyle  = createNormalStyle(wb);
            CellStyle altStyle     = createAltStyle(wb);
            CellStyle errorStyle   = createErrorStyle(wb);
            CellStyle errorBgStyle = createErrorBgStyle(wb);
            CellStyle currencyStyle      = createCurrencyStyle(wb, normalStyle);
            CellStyle currencyAltStyle   = createCurrencyStyle(wb, altStyle);
            CellStyle currencyErrorStyle = createCurrencyStyle(wb, errorBgStyle);

            // ── Title ─────────────────────────────────────────
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO NGÀY " +
                date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            XSSFCellStyle titleStyle = wb.createCellStyle();
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(new XSSFColor(hexToBytes(COLOR_HEADER_BG), null));
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 11));

            // ── Headers ───────────────────────────────────────
            String[] headers = {
                "Mã SP", "Tên SP", "Giá Cost", "Giá Bán",
                "SL Dự Kiến", "SL Thực Tế", "SL Bán Ra",
                "SL Còn Lại", "SL Hủy", "Tổng Bán (VND)",
                "Lợi Nhuận (VND)", "Ghi Chú / Lỗi"
            };

            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ─────────────────────────────────────
            int rowIdx = 3;
            BigDecimal totalRevenue    = BigDecimal.ZERO;
            BigDecimal totalProfit     = BigDecimal.ZERO;
            int errorCount = 0;

            for (int i = 0; i < records.size(); i++) {
                DailyReconcile r = records.get(i);
                boolean hasError = r.getOverallStatus() != ReconcileStatus.OK
                                && r.getOverallStatus() != ReconcileStatus.PENDING;
                boolean isAlt    = (i % 2 == 1);

                CellStyle baseStyle = hasError ? errorBgStyle
                                    : isAlt    ? altStyle
                                    :            normalStyle;
                CellStyle numStyle  = hasError ? currencyErrorStyle
                                    : isAlt    ? currencyAltStyle
                                    :            currencyStyle;

                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(18);

                if (hasError) errorCount++;

                // Col 0: Mã SP
                setCell(row, 0, r.getProduct().getCode(), baseStyle);
                // Col 1: Tên SP
                setCell(row, 1, r.getProduct().getName(), baseStyle);
                // Col 2: Giá Cost
                setCellNum(row, 2, r.getCostPerUnit(), numStyle);
                // Col 3: Giá Bán
                setCellNum(row, 3, r.getUnitPrice(), numStyle);
                // Col 4: SL Dự Kiến
                setCellNum(row, 4, r.getQtyRequested(), baseStyle);
                // Col 5: SL Thực Tế
                setCellNum(row, 5, r.getQtyProduced(), baseStyle);
                // Col 6: SL Bán Ra (POS)
                setCellNum(row, 6, r.getQtySoldPos(), baseStyle);
                // Col 7: SL Còn Lại
                setCellNum(row, 7, r.getQtyClosing(), baseStyle);
                // Col 8: SL Hủy
                setCellNum(row, 8, r.getQtyCancelled(), baseStyle);
                // Col 9: Tổng Bán
                setCellNum(row, 9, r.getRevenue(), numStyle);
                // Col 10: Lợi Nhuận
                setCellNum(row, 10, r.getGrossProfit(), numStyle);
                // Col 11: Ghi chú / Lỗi
                Cell noteCell = row.createCell(11);
                String note = buildNote(r);
                noteCell.setCellValue(note);
                if (hasError) {
                    // Chữ đỏ cho cột lỗi
                    XSSFCellStyle errTextStyle = wb.createCellStyle();
                    errTextStyle.cloneStyleFrom(errorBgStyle);
                    XSSFFont errFont = wb.createFont();
                    errFont.setColor(new XSSFColor(hexToBytes(COLOR_ERROR_FG), null));
                    errFont.setBold(true);
                    errTextStyle.setFont(errFont);
                    noteCell.setCellStyle(errTextStyle);
                } else {
                    noteCell.setCellStyle(baseStyle);
                }

                // Accumulate totals
                if (r.getRevenue() != null)    totalRevenue = totalRevenue.add(r.getRevenue());
                if (r.getGrossProfit() != null) totalProfit  = totalProfit.add(r.getGrossProfit());
            }

            // ── Total row ─────────────────────────────────────
            Row totalRow = sheet.createRow(rowIdx + 1);
            totalRow.setHeightInPoints(20);
            XSSFCellStyle totalStyle = wb.createCellStyle();
            XSSFFont totalFont = wb.createFont();
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 11);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(hexToBytes("DDEBF7"), null));
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(totalStyle);

            XSSFCellStyle totalNumStyle = wb.createCellStyle();
            totalNumStyle.cloneStyleFrom(totalStyle);
            DataFormat fmt = wb.createDataFormat();
            totalNumStyle.setDataFormat(fmt.getFormat("#,##0"));

            setCell(totalRow, 0, "TỔNG CỘNG", totalStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(
                rowIdx + 1, rowIdx + 1, 0, 8));
            setCellNum(totalRow, 9,  totalRevenue, totalNumStyle);
            setCellNum(totalRow, 10, totalProfit,  totalNumStyle);

            if (errorCount > 0) {
                XSSFCellStyle errSummaryStyle = wb.createCellStyle();
                errSummaryStyle.cloneStyleFrom(totalStyle);
                XSSFFont errSFont = wb.createFont();
                errSFont.setBold(true);
                errSFont.setColor(new XSSFColor(hexToBytes(COLOR_ERROR_FG), null));
                errSummaryStyle.setFont(errSFont);
                Cell errCell = totalRow.createCell(11);
                errCell.setCellValue("⚠ " + errorCount + " sản phẩm có lỗi");
                errCell.setCellStyle(errSummaryStyle);
            }

            // ── Column widths ─────────────────────────────────
            int[] widths = {12, 22, 14, 12, 13, 13, 12, 12, 10, 18, 18, 35};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            // Freeze header
            sheet.createFreezePane(0, 3);

            // Auto filter
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                2, rowIdx, 0, headers.length - 1));

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Note builder ──────────────────────────────────────────
    private String buildNote(DailyReconcile r) {
        if (r.getDiscrepancyNote() != null && !r.getDiscrepancyNote().isBlank()) {
            return r.getDiscrepancyNote();
        }
        return r.getOverallStatus() == ReconcileStatus.OK ? "✓ OK" : "";
    }

    // ── Style factories ───────────────────────────────────────
    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_HEADER_BG), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(hexToBytes(COLOR_HEADER_FG), null));
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createNormalStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createAltStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ALT_ROW), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createErrorStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setColor(new XSSFColor(hexToBytes(COLOR_ERROR_FG), null));
        f.setBold(true);
        s.setFont(f);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createErrorBgStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ERROR_BG), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createCurrencyStyle(XSSFWorkbook wb, CellStyle base) {
        XSSFCellStyle s = wb.createCellStyle();
        s.cloneStyleFrom(base);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        return s;
    }

    private void setBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    // ── Cell helpers ──────────────────────────────────────────
    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private void setCellNum(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) c.setCellValue(value.doubleValue());
        else               c.setCellValue(0);
        c.setCellStyle(style);
    }

    // ── Hex color helper ──────────────────────────────────────
    private byte[] hexToBytes(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }
}
