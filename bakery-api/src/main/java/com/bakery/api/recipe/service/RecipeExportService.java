/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.recipe.service;

import com.bakery.api.recipe.service.RecipeCostService.CostResult;
import com.bakery.api.recipe.service.RecipeCostService.LineCost;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Xuất công thức sản phẩm / bán thành phẩm ra file Excel.
 * Mỗi item → 1 sheet. Hỗ trợ BOM 2 tầng (SemiProduct sub-breakdown).
 */
@Service
@RequiredArgsConstructor
public class RecipeExportService {

    private final RecipeCostService recipeCostService;

    public byte[] exportRecipes(List<UUID> itemIds) {
        List<CostResult> results = recipeCostService.calculateBatch(itemIds);
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle titleStyle   = titleStyle(wb);
            CellStyle headerStyle  = headerStyle(wb);
            CellStyle labelStyle   = labelStyle(wb);
            CellStyle numStyle     = numStyle(wb);
            CellStyle subStyle     = subStyle(wb);
            CellStyle totalStyle   = totalStyle(wb);
            CellStyle missingStyle = missingStyle(wb);

            for (CostResult result : results) {
                String sheetName = result.itemCode().replaceAll("[\\[\\]\\*\\?/\\\\:]", "-");
                if (sheetName.length() > 31) sheetName = sheetName.substring(0, 31);
                Sheet sheet = wb.createSheet(sheetName);
                sheet.setColumnWidth(0, 700);
                sheet.setColumnWidth(1, 4000);
                sheet.setColumnWidth(2, 7000);
                sheet.setColumnWidth(3, 3500);
                sheet.setColumnWidth(4, 2500);
                sheet.setColumnWidth(5, 3000);
                sheet.setColumnWidth(6, 3500);
                sheet.setColumnWidth(7, 3500);
                sheet.setColumnWidth(8, 4000);

                int row = 0;

                Row r0 = sheet.createRow(row++);
                r0.setHeightInPoints(22);
                Cell t = r0.createCell(1);
                t.setCellValue("CÔNG THỨC SẢN XUẤT — " + result.itemName() + " (" + result.itemCode() + ")");
                t.setCellStyle(titleStyle);
                sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 1, 8));

                Row r1 = sheet.createRow(row++);
                cell(r1, 1, "Tổng chi phí / đvt:", labelStyle);
                Cell costCell = r1.createCell(2);
                costCell.setCellValue(result.totalCostPerUnit().setScale(0, RoundingMode.HALF_UP).doubleValue());
                costCell.setCellStyle(numStyle);
                cell(r1, 3, "đ", labelStyle);
                cell(r1, 5, result.complete() ? "✓ Đầy đủ giá" : "⚠ Thiếu giá một số NL", labelStyle);

                row++;

                Row hdr = sheet.createRow(row++);
                hdr.setHeightInPoints(18);
                String[] cols = {"", "Mã NL", "Tên nguyên liệu", "Loại", "ĐVT", "Số lượng", "Đơn giá (đ)", "Thành tiền (đ)", "Nguồn giá"};
                for (int c = 0; c < cols.length; c++) {
                    Cell hc = hdr.createCell(c);
                    hc.setCellValue(cols[c]);
                    hc.setCellStyle(headerStyle);
                }

                BigDecimal total = BigDecimal.ZERO;
                for (LineCost line : result.breakdown()) {
                    row = writeLineCost(sheet, row, line, 0, numStyle, subStyle, missingStyle, labelStyle);
                    total = total.add(line.lineCost());
                }

                Row totRow = sheet.createRow(row);
                cell(totRow, 1, "TỔNG CHI PHÍ / ĐƠN VỊ SẢN PHẨM", totalStyle);
                sheet.addMergedRegion(new CellRangeAddress(row, row, 1, 6));
                Cell totCell = totRow.createCell(7);
                totCell.setCellValue(total.setScale(0, RoundingMode.HALF_UP).doubleValue());
                totCell.setCellStyle(totalStyle);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất Excel công thức: " + e.getMessage(), e);
        }
    }

    private int writeLineCost(Sheet sheet, int row, LineCost line, int depth,
                               CellStyle numStyle, CellStyle subStyle,
                               CellStyle missingStyle, CellStyle labelStyle) {
        boolean isMissing = "MISSING".equals(line.priceSource()) || "UNIT_MISMATCH".equals(line.priceSource());
        boolean isSub     = depth > 0;
        CellStyle baseStyle = isMissing ? missingStyle : (isSub ? subStyle : null);

        Row r = sheet.createRow(row++);
        if (depth > 0) cell(r, 0, "↳", labelStyle);

        cell(r, 1, line.itemCode(), baseStyle);
        cell(r, 2, "  ".repeat(depth) + line.itemName(), baseStyle);
        cell(r, 3, friendlyType(line.itemType()), baseStyle);
        cell(r, 4, line.unit(), baseStyle);

        Cell qtyCell = r.createCell(5);
        qtyCell.setCellValue(line.quantity().doubleValue());
        qtyCell.setCellStyle(baseStyle != null ? baseStyle : numStyle);

        Cell upCell = r.createCell(6);
        upCell.setCellValue(line.unitPrice().setScale(0, RoundingMode.HALF_UP).doubleValue());
        upCell.setCellStyle(baseStyle != null ? baseStyle : numStyle);

        Cell lcCell = r.createCell(7);
        lcCell.setCellValue(line.lineCost().setScale(0, RoundingMode.HALF_UP).doubleValue());
        lcCell.setCellStyle(baseStyle != null ? baseStyle : numStyle);

        cell(r, 8, friendlySource(line.priceSource()), isMissing ? missingStyle : labelStyle);

        if (line.subBreakdown() != null && !line.subBreakdown().isEmpty()) {
            for (LineCost sub : line.subBreakdown()) {
                row = writeLineCost(sheet, row, sub, depth + 1, numStyle, subStyle, missingStyle, labelStyle);
            }
        }
        return row;
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    private static String friendlyType(String t) {
        return switch (t) {
            case "INGREDIENT"   -> "Nguyên liệu";
            case "SEMI_PRODUCT" -> "Bán TP";
            default             -> t;
        };
    }

    private static String friendlySource(String s) {
        return switch (s) {
            case "CATALOG"           -> "Bảng giá";
            case "STOCK_LOT_AVG"     -> "TB tồn kho";
            case "RECIPE_CALCULATED" -> "Tính từ CT";
            case "MISSING"           -> "⚠ Chưa có giá";
            case "UNIT_MISMATCH"     -> "⚠ Sai đvt";
            default                  -> s;
        };
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle labelStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        setBorder(s);
        return s;
    }

    private CellStyle numStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        setBorder(s);
        return s;
    }

    private CellStyle subStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s);
        return s;
    }

    private CellStyle totalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        setBorder(s);
        return s;
    }

    private CellStyle missingStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.RED.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s);
        return s;
    }

    private static void setBorder(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}
