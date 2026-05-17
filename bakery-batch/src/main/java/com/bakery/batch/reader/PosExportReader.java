package com.bakery.batch.reader;

import com.bakery.batch.dto.PosTransactionRow;
import com.bakery.batch.util.ExcelSheetParser;
import com.bakery.batch.util.ErrorRowCollector;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc file BigProductBySaleByCat.xlsx — Export từ máy POS.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  FILE STRUCTURE — thay đổi ở đây nếu file thay đổi        │
 * ├──────────────────────────────────────────────────────────── │
 * │  Row 1:  Ngày lập (col 1)                                  │
 * │  Row 5:  Chi nhánh (col 1)                                 │
 * │  Row 8:  Header columns                                     │
 * │  Row 9:  Summary row → SKIP                                │
 * │  Row 10: Trống                                              │
 * │  Row 11+: Data rows                                         │
 * └─────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  COLUMN MAPPING — SỬA ĐÂY nếu file thay đổi cột           │
 * ├──────┬──────────────────────┬──────────────────────────────┤
 * │ Col  │ Tên cột trong file   │ Mapping vào DTO              │
 * ├──────┼──────────────────────┼──────────────────────────────┤
 * │  1   │ Mã hàng              │ productCode                  │
 * │  2   │ Tên hàng             │ productName                  │
 * │  5   │ SL bán               │ qtySold                      │
 * │  6   │ Doanh thu            │ revenue                      │
 * │  7   │ SL trả               │ qtyReturned                  │
 * │  10  │ Doanh thu thuần      │ netRevenue                   │
 * └──────┴──────────────────────┴──────────────────────────────┘
 */
@Slf4j
public class PosExportReader {

    // ── File structure config ─────────────────────────────────
    private static final int ROW_BRANCH_INFO = 4;   // Row 5 (0-based=4): Chi nhánh
    private static final int COL_BRANCH_INFO = 1;   // Col B
    private static final int DATA_START_ROW  = 10;  // Row 11 (0-based=10): data bắt đầu

    // ── Column mapping — SỬA ĐÂY nếu file thay đổi cột ──────
    private static final int COL_PRODUCT_CODE = 1;  // Mã hàng
    private static final int COL_PRODUCT_NAME = 2;  // Tên hàng
    private static final int COL_QTY_SOLD     = 5;  // SL bán
    private static final int COL_REVENUE      = 6;  // Doanh thu
    private static final int COL_QTY_RETURNED = 7;  // SL trả
    private static final int COL_NET_REVENUE  = 10; // Doanh thu thuần

    public static List<PosTransactionRow> read(
            String filePath,
            ErrorRowCollector collector,
            LocalDate processDate) throws IOException {

        List<PosTransactionRow> result = new ArrayList<>();

        try (Workbook wb = ExcelSheetParser.openWorkbook(filePath)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                log.error("File POS không có sheet nào: {}", filePath);
                return result;
            }

            // Branch name từ file
            String branchName = extractBranchName(sheet);
            log.info("POS file | Ngày: {} | Chi nhánh: {}", processDate, branchName);

            // Đọc data từ DATA_START_ROW
            for (int i = DATA_START_ROW; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                collector.incrementTotal();

                try {
                    String productCode = ExcelSheetParser.getCellStringValue(
                        row.getCell(COL_PRODUCT_CODE)
                    );

                    // Chỉ lấy dòng có Mã SP hợp lệ
                    if (productCode == null || !productCode.toUpperCase().startsWith("SP")) {
                        continue;
                    }

                    BigDecimal qtySold    = ExcelSheetParser.getCellDecimalValue(row.getCell(COL_QTY_SOLD));
                    BigDecimal revenue    = ExcelSheetParser.getCellDecimalValue(row.getCell(COL_REVENUE));
                    BigDecimal qtyRet     = ExcelSheetParser.getCellDecimalValue(row.getCell(COL_QTY_RETURNED));
                    BigDecimal netRevenue = ExcelSheetParser.getCellDecimalValue(row.getCell(COL_NET_REVENUE));
                    String productName    = ExcelSheetParser.getCellStringValue(row.getCell(COL_PRODUCT_NAME));

                    if (qtySold == null || qtySold.compareTo(BigDecimal.ZERO) <= 0) {
                        log.debug("Bỏ qua {} - SL bán = 0", productCode);
                        continue;
                    }

                    result.add(PosTransactionRow.builder()
                        .transactionDate(processDate)
                        .branchName(branchName)
                        .productCode(productCode.trim().toUpperCase())
                        .productName(productName)
                        .qtySold(qtySold)
                        .revenue(nullSafe(revenue))
                        .qtyReturned(nullSafe(qtyRet))
                        .netRevenue(nullSafe(netRevenue))
                        .rowIndex(i)
                        .build());

                    collector.incrementSuccess();

                } catch (Exception e) {
                    log.error("Lỗi đọc row POS {}: {}", i, e.getMessage());
                    collector.addError(i, "unknown", e.getMessage());
                }
            }
        }

        log.info("PosExportReader: {} raw rows (trước aggregate)", result.size());
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────

    private static String extractBranchName(Sheet sheet) {
        try {
            Row row = sheet.getRow(ROW_BRANCH_INFO);
            if (row == null) return "UNKNOWN";
            String text = ExcelSheetParser.getCellStringValue(row.getCell(COL_BRANCH_INFO));
            if (text != null && text.contains("Chi nhánh:")) {
                return text.replace("Chi nhánh:", "").trim();
            }
            return text != null ? text : "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private static BigDecimal nullSafe(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
