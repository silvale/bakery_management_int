package com.bakery.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Xuất file TongHop_{date}.xlsx.
 *
 * Tổng hợp kết quả đối chiếu cuối ngày:
 *   - Tồn hôm trước | Bánh sáng | Bán POS | NV báo bán | Hủy | Tồn tối NV | Tồn tối HT | Chênh lệch | Status
 *
 * Highlight đỏ các dòng có chênh lệch.
 * Output: /output/tong-hop/TongHop_{ddMMyyyy}.xlsx
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TongHopExcelService {

    @Value("${bakery.batch.output-dir:./batch-output}")
    private String outputDir;

    private static final String COLOR_HEADER   = "1F3864";
    private static final String COLOR_HEADER_FG= "FFFFFF";
    private static final String COLOR_OK       = "E2EFDA"; // xanh nhạt
    private static final String COLOR_ERROR    = "FFC7CE"; // đỏ nhạt
    private static final String COLOR_NOTFOUND = "D9D9D9"; // xám
    private static final String COLOR_ALT      = "F5F5F5";

    public Path generate(LocalDate processDate,
                         List<BaoCaoNgayProcessorService.CompareResult> results) throws Exception {

        byte[] bytes = buildExcel(results, processDate);

        String filename = "TongHop_" + processDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";
        Path outPath = Paths.get(outputDir, "tong-hop");
        Files.createDirectories(outPath);
        Path filePath = outPath.resolve(filename);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(bytes);
        }

        log.info("✓ TongHop: {} | {} dòng", filePath, results.size());
        return filePath;
    }

    private byte[] buildExcel(List<BaoCaoNgayProcessorService.CompareResult> results,
                               LocalDate processDate) throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Tổng Hợp");

            CellStyle headerStyle   = createHeaderStyle(wb);
            CellStyle okStyle       = createColoredStyle(wb, COLOR_OK);
            CellStyle errorStyle    = createColoredStyle(wb, COLOR_ERROR);
            CellStyle notFoundStyle = createColoredStyle(wb, COLOR_NOTFOUND);
            CellStyle altStyle      = createBodyStyle(wb, true);
            CellStyle normalStyle   = createBodyStyle(wb, false);

            // Row 0: Tiêu đề
            Row titleRow = sheet.createRow(0);
            setCell(titleRow, 0,
                "TỔNG HỢP CUỐI NGÀY — " + processDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            // Row 1: Header
            String[] headers = {
                "STT", "Mã SP", "Tên SP",
                "Tồn hôm trước", "Bánh sáng",
                "Bán POS", "NV báo bán",
                "Hủy", "Tồn tối NV",
                "Tồn tối HT", "Chênh lệch", "Status"
            };
            Row hRow = sheet.createRow(1);
            for (int c = 0; c < headers.length; c++) {
                setCell(hRow, c, headers[c], headerStyle);
            }

            // Data
            int stt = 1;
            for (int i = 0; i < results.size(); i++) {
                BaoCaoNgayProcessorService.CompareResult r = results.get(i);
                boolean alt = (i % 2 == 1);

                CellStyle rowStyle = switch (r.status()) {
                    case "CHÊNH LỆCH" -> errorStyle;
                    case "NOT_FOUND"  -> notFoundStyle;
                    default           -> alt ? altStyle : normalStyle;
                };

                Row row = sheet.createRow(i + 2);
                row.setHeightInPoints(18);

                setCell(row, 0,  stt++, rowStyle);
                setCell(row, 1,  r.masp(), rowStyle);
                setCell(row, 2,  r.tenBanh() != null ? r.tenBanh() : "", rowStyle);
                setCellNum(row, 3,  r.tonHomTruoc(), rowStyle);
                setCellNum(row, 4,  r.banhSang(), rowStyle);
                setCellNum(row, 5,  r.soldPos(), rowStyle);
                setCellNum(row, 6,  r.soldReported(), rowStyle);
                setCellNum(row, 7,  r.huy(), rowStyle);
                setCellNum(row, 8,  r.tonToi(), rowStyle);
                setCellNum(row, 9,  r.systemClosing(), rowStyle);
                setCellNum(row, 10, r.diff(), rowStyle);
                setCell(row, 11, r.status(), rowStyle);
            }

            // Summary
            long errorCount = results.stream().filter(BaoCaoNgayProcessorService.CompareResult::hasDiscrepancy).count();
            int sumRow = results.size() + 3;
            Row sum = sheet.createRow(sumRow);
            setCell(sum, 0, String.format("Tổng: %d sản phẩm | %d chênh lệch",
                results.size(), errorCount), headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(sumRow, sumRow, 0, 11));

            // Note
            Row note = sheet.createRow(sumRow + 2);
            setCell(note, 0, "* Nền đỏ: chênh lệch > 1 cái/kg cần kiểm tra lại", errorStyle);
            sheet.addMergedRegion(new CellRangeAddress(sumRow + 2, sumRow + 2, 0, 5));

            // Column widths
            int[] widths = {6, 14, 28, 14, 12, 10, 12, 8, 12, 12, 12, 12};
            for (int c = 0; c < widths.length; c++) {
                sheet.setColumnWidth(c, widths[c] * 256);
            }
            sheet.createFreezePane(0, 2);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Style helpers ─────────────────────────────────────────

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_HEADER), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(hexToBytes(COLOR_HEADER_FG), null));
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createColoredStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(hex), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createBodyStyle(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ALT), null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private void setBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private void setCell(Row row, int col, Object val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val instanceof String v)      c.setCellValue(v);
        else if (val instanceof Integer v) c.setCellValue(v);
        c.setCellStyle(style);
    }

    private void setCellNum(Row row, int col, BigDecimal val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val != null) c.setCellValue(val.doubleValue());
        c.setCellStyle(style);
    }

    private byte[] hexToBytes(String hex) {
        return new byte[]{
            (byte) Integer.parseInt(hex.substring(0, 2), 16),
            (byte) Integer.parseInt(hex.substring(2, 4), 16),
            (byte) Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
}
