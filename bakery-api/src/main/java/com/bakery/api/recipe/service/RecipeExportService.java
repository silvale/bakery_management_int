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
import java.math.RoundingMode;
import java.util.List;

/**
 * Xuất công thức sản phẩm / bán thành phẩm ra file Excel (dành cho bếp).
 * Tất cả sản phẩm trong 1 sheet. Chỉ hiển thị: Tên NL, ĐVT, Số lượng.
 */
@Service
@RequiredArgsConstructor
public class RecipeExportService {

    private final RecipeCostService recipeCostService;

    public byte[] exportRecipes(List<java.util.UUID> itemIds) {
        List<CostResult> results = recipeCostService.calculateBatch(itemIds);
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Công thức");
            sheet.setColumnWidth(0, 800);    // indent
            sheet.setColumnWidth(1, 8000);   // tên nguyên liệu
            sheet.setColumnWidth(2, 3000);   // ĐVT
            sheet.setColumnWidth(3, 3500);   // số lượng

            // Shared styles
            CellStyle titleStyle  = titleStyle(wb);
            CellStyle headerStyle = headerStyle(wb);
            CellStyle dataStyle   = dataStyle(wb);
            CellStyle subStyle    = subStyle(wb);
            CellStyle numStyle    = numStyle(wb);
            CellStyle subNumStyle = subNumStyle(wb);

            int row = 0;

            for (CostResult result : results) {
                // ── Tiêu đề sản phẩm ──────────────────────────────────
                Row titleRow = sheet.createRow(row++);
                titleRow.setHeightInPoints(20);
                Cell tc = titleRow.createCell(0);
                tc.setCellValue(result.itemName() + "  (" + result.itemCode() + ")");
                tc.setCellStyle(titleStyle);
                sheet.addMergedRegion(new CellRangeAddress(row - 1, row - 1, 0, 3));

                // ── Header cột ────────────────────────────────────────
                Row hdr = sheet.createRow(row++);
                hdr.setHeightInPoints(16);
                String[] cols = {"", "Tên nguyên liệu", "ĐVT", "Số lượng"};
                for (int c = 0; c < cols.length; c++) {
                    Cell hc = hdr.createCell(c);
                    hc.setCellValue(cols[c]);
                    hc.setCellStyle(headerStyle);
                }

                // ── Dòng nguyên liệu ──────────────────────────────────
                for (LineCost line : result.breakdown()) {
                    row = writeLine(sheet, row, line, 0, dataStyle, subStyle, numStyle, subNumStyle);
                }

                row++; // blank row giữa các sản phẩm
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất Excel công thức: " + e.getMessage(), e);
        }
    }

    private int writeLine(Sheet sheet, int row, LineCost line, int depth,
                          CellStyle dataStyle, CellStyle subStyle,
                          CellStyle numStyle, CellStyle subNumStyle) {
        boolean isSub = depth > 0;
        Row r = sheet.createRow(row++);

        // indent marker
        if (isSub) {
            Cell ic = r.createCell(0);
            ic.setCellValue("↳");
            ic.setCellStyle(subStyle);
        }

        Cell nameCell = r.createCell(1);
        nameCell.setCellValue("  ".repeat(depth) + line.itemName());
        nameCell.setCellStyle(isSub ? subStyle : dataStyle);

        Cell unitCell = r.createCell(2);
        unitCell.setCellValue(line.unit() != null ? line.unit() : "");
        unitCell.setCellStyle(isSub ? subStyle : dataStyle);

        Cell qtyCell = r.createCell(3);
        qtyCell.setCellValue(line.quantity().setScale(3, RoundingMode.HALF_UP).doubleValue());
        qtyCell.setCellStyle(isSub ? subNumStyle : numStyle);

        // Đệ quy cho BTP
        if (line.subBreakdown() != null && !line.subBreakdown().isEmpty()) {
            for (LineCost sub : line.subBreakdown()) {
                row = writeLine(sheet, row, sub, depth + 1, dataStyle, subStyle, numStyle, subNumStyle);
            }
        }
        return row;
    }

    // ── Styles ────────────────────────────────────────────────────

    private CellStyle titleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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

    private CellStyle dataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        setBorder(s);
        return s;
    }

    private CellStyle numStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0.###"));
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

    private CellStyle subNumStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFont(f);
        DataFormat df = wb.createDataFormat();
        s.setDataFormat(df.getFormat("#,##0.###"));
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
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
