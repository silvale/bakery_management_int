package com.bakery.api.service;

import com.bakery.common.entity.Branch;
import com.bakery.common.entity.ProductionLot;
import com.bakery.common.repository.ProductionLotRepository;
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
 * Xuất file HuyBanh_{date}.xlsx.
 *
 * Danh sách các lô bánh sắp hết hạn (HSD = hôm nay hoặc ngày mai)
 * còn tồn kho → NV cần huỷ.
 *
 * Output: /output/huy-banh/HuyBanh_{ddMMyyyy}.xlsx
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HuyBanhExcelService {

    private final ProductionLotRepository productionLotRepository;

    @Value("${bakery.batch.output-dir:./batch-output}")
    private String outputDir;

    private static final String COLOR_HEADER    = "C00000"; // Đỏ đậm
    private static final String COLOR_HEADER_FG = "FFFFFF";
    private static final String COLOR_EXPIRED   = "FFC7CE"; // Hồng nhạt — hết hạn hôm nay
    private static final String COLOR_EXPIRING  = "FFEB9C"; // Vàng nhạt — hết hạn ngày mai
    private static final String COLOR_ALT       = "F5F5F5";

    /**
     * Tạo file HuyBanh cho ngày processDate.
     * Lấy tất cả lô có expiryDate <= processDate + 1 và còn tồn kho.
     */
    public Path generate(LocalDate processDate, Branch shopBranch) throws Exception {
        log.info("Generate HuyBanh | date={}", processDate);

        // Lấy lô hết hạn hôm nay + ngày mai
        LocalDate warningUntil = processDate.plusDays(1);
        List<ProductionLot> lots = productionLotRepository
            .findExpiringLots(shopBranch.getId(), warningUntil);

        // Chỉ giữ lô còn hàng
        List<ProductionLot> toCancel = lots.stream()
            .filter(lot -> {
                BigDecimal rem = lot.getQtyRemaining();
                return rem != null && rem.compareTo(BigDecimal.ZERO) > 0;
            })
            .toList();

        byte[] bytes = buildExcel(toCancel, processDate);

        // Save file
        String filename = "HuyBanh_" + processDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";
        Path outPath = Paths.get(outputDir, "huy-banh");
        Files.createDirectories(outPath);
        Path filePath = outPath.resolve(filename);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(bytes);
        }

        log.info("✓ HuyBanh: {} lô cần huỷ → {}", toCancel.size(), filePath);
        return filePath;
    }

    // ── Build Excel ───────────────────────────────────────────

    private byte[] buildExcel(List<ProductionLot> lots, LocalDate processDate) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Huỷ Bánh");

            CellStyle headerStyle   = createHeaderStyle(wb);
            CellStyle normalStyle   = createBodyStyle(wb, false);
            CellStyle altStyle      = createBodyStyle(wb, true);
            CellStyle expiredStyle  = createColorStyle(wb, COLOR_EXPIRED);
            CellStyle expiringStyle = createColorStyle(wb, COLOR_EXPIRING);
            CellStyle numNormal     = createNumStyle(wb, false);
            CellStyle numAlt        = createNumStyle(wb, true);

            // Row 0: tiêu đề
            Row titleRow = sheet.createRow(0);
            setCell(titleRow, 0, "DANH SÁCH BÁNH CẦN HUỶ — " +
                processDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            // Row 1: header
            String[] headers = {"STT", "Mã SP", "Tên SP", "Ngày SX", "Ngày HSD", "SX", "Đã bán", "Cần Huỷ"};
            Row hRow = sheet.createRow(1);
            for (int c = 0; c < headers.length; c++) {
                setCell(hRow, c, headers[c], headerStyle);
            }

            // Data rows
            int stt = 1;
            for (int i = 0; i < lots.size(); i++) {
                ProductionLot lot = lots.get(i);
                boolean isToday = lot.getExpiryDate().equals(processDate);
                boolean alt     = (i % 2 == 1);

                CellStyle baseStyle = isToday ? expiredStyle
                    : (lot.getExpiryDate().equals(processDate.plusDays(1)) ? expiringStyle
                    : (alt ? altStyle : normalStyle));
                CellStyle ns = isToday ? expiredStyle : (alt ? numAlt : numNormal);

                Row row = sheet.createRow(i + 2);
                row.setHeightInPoints(18);

                setCell(row, 0, stt++, baseStyle);
                setCell(row, 1, lot.getProduct().getCode(), baseStyle);
                setCell(row, 2, lot.getProduct().getName(), baseStyle);
                setCell(row, 3, lot.getProductionDate().format(DateTimeFormatter.ofPattern("dd/MM")), baseStyle);
                setCell(row, 4, lot.getExpiryDate().format(DateTimeFormatter.ofPattern("dd/MM")), baseStyle);
                setCellNum(row, 5, lot.getQtyProduced(), ns);
                setCellNum(row, 6, lot.getQtySold(), ns);
                setCellNum(row, 7, lot.getQtyRemaining() != null
                    ? lot.getQtyRemaining() : BigDecimal.ZERO, ns);
            }

            // Tổng
            int totalRow = lots.size() + 2;
            Row sumRow = sheet.createRow(totalRow);
            setCell(sumRow, 0, "Tổng", headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(totalRow, totalRow, 0, 6));
            BigDecimal totalCancel = lots.stream()
                .map(l -> l.getQtyRemaining() != null ? l.getQtyRemaining() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            setCellNum(sumRow, 7, totalCancel, headerStyle);

            // Note chú thích màu
            int noteRow = totalRow + 2;
            Row note1 = sheet.createRow(noteRow);
            setCell(note1, 0, "* Nền đỏ: hết hạn HÔM NAY", createColorStyle(wb, COLOR_EXPIRED));
            sheet.addMergedRegion(new CellRangeAddress(noteRow, noteRow, 0, 3));
            Row note2 = sheet.createRow(noteRow + 1);
            setCell(note2, 0, "* Nền vàng: hết hạn NGÀY MAI", createColorStyle(wb, COLOR_EXPIRING));
            sheet.addMergedRegion(new CellRangeAddress(noteRow + 1, noteRow + 1, 0, 3));

            // Column widths
            sheet.setColumnWidth(0, 6  * 256);
            sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 28 * 256);
            sheet.setColumnWidth(3, 10 * 256);
            sheet.setColumnWidth(4, 10 * 256);
            sheet.setColumnWidth(5, 10 * 256);
            sheet.setColumnWidth(6, 10 * 256);
            sheet.setColumnWidth(7, 12 * 256);

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

    private CellStyle createColorStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(hex), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createNumStyle(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ALT), null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setAlignment(HorizontalAlignment.CENTER);
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
        if (val instanceof String v)     c.setCellValue(v);
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
