package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Auto-generate file BanhRaNgay.xlsx cho ngày hôm sau.
 *
 * Logic:
 *   qty_to_produce = MAX(0, template_default_qty - qty_closing_today)
 *
 * Output: batch-output/BanhRaNgay_{ddMMyyyy}.xlsx
 * Admin review + chỉnh sửa + chạy lại nếu cần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanhRaNgayGeneratorService {

    private final ProductionTemplateRepository templateRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final DailyInventoryRepository      dailyInventoryRepository;
    private final BranchRepository              branchRepository;
    private final ProductRepository             productRepository;

    @Value("${bakery.batch.output-dir:./batch-output}")
    private String outputDir;

    private static final String COLOR_HEADER   = "2F5496";
    private static final String COLOR_HEADER_FG= "FFFFFF";
    private static final String COLOR_ALT      = "F5F5F5";
    private static final String COLOR_ZERO     = "FCE4D6"; // cam nhạt — sản xuất 0

    /**
     * Generate file BanhRaNgay cho ngày targetDate.
     * Dựa vào tồn kho ngày hôm nay (sourceDate).
     *
     * @param sourceDate Ngày lấy tồn kho (thường = hôm nay)
     * @param targetDate Ngày sản xuất (thường = ngày mai)
     */
    @Transactional(readOnly = true)
    public Path generate(LocalDate sourceDate, LocalDate targetDate) throws Exception {
        log.info("Generate BanhRaNgay | Tồn từ: {} | Sản xuất: {}", sourceDate, targetDate);

        Branch shopBranch = branchRepository.findAll().stream()
            .filter(b -> !b.getIsMain())
            .findFirst()
            .orElseThrow();

        Branch kitchenBranch = branchRepository.findByIsMainTrue()
            .orElseThrow();

        // Load tất cả template đang active
        List<ProductionTemplate> templates = templateRepository.findAllByIsActiveTrue();

        // Load tồn kho hôm nay
        List<DailyInventory> inventories = dailyInventoryRepository
            .findAllWithProductByBranchIdAndDate(shopBranch.getId(), sourceDate);

        // Build map: productId → qty_closing
        Map<UUID, BigDecimal> closingMap = new HashMap<>();
        for (DailyInventory inv : inventories) {
            closingMap.put(inv.getProduct().getId(), inv.getQtyClosing());
        }

        // Build danh sách sản phẩm cần sản xuất
        List<ProductionItem> items = new ArrayList<>();
        for (ProductionTemplate tmpl : templates) {
            Product product = tmpl.getProduct();
            BigDecimal qtyClosing = closingMap.getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal qtyToProduce = tmpl.getDefaultQty().subtract(qtyClosing)
                .max(BigDecimal.ZERO);

            items.add(new ProductionItem(
                product.getCode(),
                product.getName(),
                product.getUnit(),
                tmpl.getDefaultQty(),
                qtyClosing,
                qtyToProduce
            ));
        }

        // Sort: sheet B.MÌ (PCS) trước, LAN (KG) sau
        items.sort(Comparator.comparing(ProductionItem::unit).reversed()
            .thenComparing(ProductionItem::productCode));

        // Generate Excel
        byte[] excelBytes = buildExcel(items, targetDate, kitchenBranch.getName());

        // Save file
        String filename = "BanhRaNgay_" + targetDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";
        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);
        Path filePath = outputPath.resolve(filename);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(excelBytes);
        }

        log.info("✓ Generated BanhRaNgay: {} | {} sản phẩm", filePath, items.size());
        return filePath;
    }

    // ── Build Excel ───────────────────────────────────────────

    private byte[] buildExcel(List<ProductionItem> items, LocalDate targetDate, String branchName)
            throws Exception {

        try (XSSFWorkbook wb = new XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {

            // Style
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle normalStyle = createNormalStyle(wb, false);
            CellStyle altStyle    = createNormalStyle(wb, true);
            CellStyle zeroStyle   = createZeroStyle(wb);
            CellStyle numStyle    = createNumStyle(wb, false);
            CellStyle numAltStyle = createNumStyle(wb, true);

            // Sheet B.MÌ
            XSSFSheet bmiSheet = wb.createSheet("B.MI");
            buildSheet(bmiSheet, items.stream()
                .filter(i -> "PCS".equals(i.unit()))
                .toList(), targetDate, headerStyle, normalStyle, altStyle, zeroStyle, numStyle, numAltStyle);

            // Sheet LAN
            XSSFSheet lanSheet = wb.createSheet("LAN");
            buildSheet(lanSheet, items.stream()
                .filter(i -> "KG".equals(i.unit()))
                .toList(), targetDate, headerStyle, normalStyle, altStyle, zeroStyle, numStyle, numAltStyle);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildSheet(XSSFSheet sheet, List<ProductionItem> items,
                             LocalDate targetDate,
                             CellStyle headerStyle, CellStyle normalStyle,
                             CellStyle altStyle, CellStyle zeroStyle,
                             CellStyle numStyle, CellStyle numAltStyle) {

        String dateStr = targetDate.format(DateTimeFormatter.ofPattern("d/M"));

        // Row 0: Title
        Row titleRow = sheet.createRow(0);
        setCell(titleRow, 0, "BÁN HÀNG", headerStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

        // Row 1: Header
        Row headerRow = sheet.createRow(1);
        setCell(headerRow, 0, "STT",   headerStyle);
        setCell(headerRow, 1, "Mã SP", headerStyle);
        setCell(headerRow, 2, "BÁNH",  headerStyle);
        setCell(headerRow, 3, "Sản Xuất/ Ngày " + dateStr, headerStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 3, 6));

        // Row 2: Sub-header
        Row subRow = sheet.createRow(2);
        setCell(subRow, 3, "dự kiến",   headerStyle);
        setCell(subRow, 4, "thực tế",   headerStyle);
        setCell(subRow, 5, "tồn tối",   headerStyle);
        setCell(subRow, 6, "hủy",       headerStyle);

        // Data rows
        int rowIdx = 3;
        int stt = 1;
        for (int i = 0; i < items.size(); i++) {
            ProductionItem item = items.get(i);
            boolean isAlt = (i % 2 == 1);
            boolean isZero = item.qtyToProduce().compareTo(BigDecimal.ZERO) == 0;

            CellStyle baseStyle = isZero ? zeroStyle : (isAlt ? altStyle : normalStyle);
            CellStyle ns = isAlt ? numAltStyle : numStyle;

            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(18);

            setCell(row, 0, stt++, baseStyle);
            setCell(row, 1, item.productCode(), baseStyle);
            setCell(row, 2, item.productName(), baseStyle);
            setCellNum(row, 3, item.qtyToProduce(), ns);  // Dự kiến
            row.createCell(4).setCellStyle(baseStyle);     // Thực tế — để trống cho bếp điền
            row.createCell(5).setCellStyle(baseStyle);     // Tồn tối — để trống
            row.createCell(6).setCellStyle(baseStyle);     // Hủy — để trống
        }

        // Column widths
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 30 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 12 * 256);
        sheet.setColumnWidth(6, 10 * 256);

        // Freeze header
        sheet.createFreezePane(0, 3);
    }

    // ── Style helpers ─────────────────────────────────────────

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_HEADER), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setBold(true); f.setColor(new XSSFColor(hexToBytes(COLOR_HEADER_FG), null));
        f.setFontHeightInPoints((short)10); f.setFontName("Arial");
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createNormalStyle(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ALT), null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle createZeroStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(hexToBytes(COLOR_ZERO), null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setColor(new XSSFColor(hexToBytes("C00000"), null));
        s.setFont(f);
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
        s.setBorderTop(BorderStyle.THIN); s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
    }

    private void setCell(Row row, int col, Object val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val instanceof String sv) c.setCellValue(sv);
        else if (val instanceof Integer iv) c.setCellValue(iv);
        c.setCellStyle(style);
    }

    private void setCellNum(Row row, int col, BigDecimal val, CellStyle style) {
        Cell c = row.createCell(col);
        if (val != null) c.setCellValue(val.doubleValue());
        c.setCellStyle(style);
    }

    private byte[] hexToBytes(String hex) {
        return new byte[]{
            (byte) Integer.parseInt(hex.substring(0,2),16),
            (byte) Integer.parseInt(hex.substring(2,4),16),
            (byte) Integer.parseInt(hex.substring(4,6),16)
        };
    }

    // ── Item record ───────────────────────────────────────────

    private record ProductionItem(
        String     productCode,
        String     productName,
        String     unit,
        BigDecimal templateQty,
        BigDecimal qtyClosing,
        BigDecimal qtyToProduce
    ) {}
}
