package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Xử lý file Báo Cáo Ngày (.xlsx) — gửi lúc ~22:00.
 *
 * Đọc Sheet "SX" (và các sheet tương tự):
 *   Cột: STT | Mã SP (IN_CODE) | Tên bánh | Tồn hôm trước | Bánh sáng | Tồn tối | ... | Hủy | ...
 *
 * Logic:
 *   1. Đọc 4 cột: tồn_hôm_trước, bánh_sáng, tồn_tối, hủy theo IN_CODE
 *   2. So sánh với hệ thống:
 *        system_closing = tồn_hôm_trước + bánh_sáng - sold(POS) - hủy
 *        diff = system_closing - tồn_tối (NV báo)
 *   3. Lưu / cập nhật DailyInventory
 *   4. Xuất TongHop_{date}.xlsx (kết quả + chênh lệch)
 *   5. Xuất BanhRaNgay_{date+1}.xlsx (kế hoạch sản xuất ngày mai)
 *
 * Gọi bởi: ReportFileWatcherService (cron 22h)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaoCaoNgayProcessorService {

    private final ProductRepository          productRepository;
    private final BranchRepository           branchRepository;
    private final DailyInventoryRepository   dailyInventoryRepository;
    private final TongHopExcelService        tongHopExcelService;
    private final BanhRaNgayGeneratorService banhRaNgayGenerator;

    // ── Cột trong sheet SX ────────────────────────────────────
    // Row 0: STT | (merged) | TÊN BÁNH | (merged) | {date} | ...
    // Row 1: (none) | (none) | (none) | Tồn hôm trước | bánh sáng | tồn tối | giảm giá | Huỷ | tổng tồn
    // Data:   1 | SP022575 | chén trứng | 0 | 20 | 16 | null | null | 4

    private static final int COL_MA_SP          = 1; // IN_CODE
    private static final int COL_TEN_BANH       = 2; // Tên bánh
    private static final int COL_TON_HOM_TRUOC  = 3; // Tồn hôm trước
    private static final int COL_BANH_SANG      = 4; // Bánh sáng
    private static final int COL_TON_TOI        = 5; // Tồn tối
    private static final int COL_GIAM_GIA       = 6; // Giảm giá (bỏ qua)
    private static final int COL_HUY            = 7; // Hủy

    // ── Process ───────────────────────────────────────────────

    @Transactional
    public ProcessResult process(Path filePath, LocalDate processDate) throws Exception {
        log.info("=== BaoCaoNgay Processor | file={} | date={} ===",
            filePath.getFileName(), processDate);

        Branch shopBranch = branchRepository.findAll().stream()
            .filter(b -> !b.getIsMain())
            .findFirst()
            .orElseThrow();

        // 1. Parse file — chỉ đọc sheet SX (index 0)
        List<ReportRow> rows = parseSheetSX(filePath, processDate);
        log.info("BaoCaoNgay parse: {} dòng", rows.size());

        // 2. Upsert DailyInventory + tính chênh lệch
        List<CompareResult> results = new ArrayList<>();

        for (ReportRow row : rows) {
            Optional<Product> productOpt = productRepository.findByCode(row.masp());
            if (productOpt.isEmpty()) {
                log.warn("Không tìm thấy SP: {}", row.masp());
                results.add(CompareResult.notFound(row.masp(), row.tenBanh()));
                continue;
            }

            Product product = productOpt.get();

            // Upsert DailyInventory
            DailyInventory inv = dailyInventoryRepository
                .findByBranchIdAndProductIdAndInventoryDate(
                    shopBranch.getId(), product.getId(), processDate)
                .orElseGet(() -> DailyInventory.builder()
                    .branch(shopBranch)
                    .product(product)
                    .inventoryDate(processDate)
                    .qtyOpening(BigDecimal.ZERO)
                    .qtyReceived(BigDecimal.ZERO)
                    .qtyCancelled(BigDecimal.ZERO)
                    .qtyClosing(BigDecimal.ZERO)
                    .qtySoldReported(BigDecimal.ZERO)
                    .build());

            // Điền dữ liệu từ BaoCaoNgay
            BigDecimal tonHomTruoc = row.tonHomTruoc();
            BigDecimal banhSang    = row.banhSang();
            BigDecimal tonToi      = row.tonToi();
            BigDecimal huy         = row.huy();

            inv.setQtyOpening(tonHomTruoc);
            inv.setQtyReceived(banhSang);
            inv.setQtyCancelled(huy);
            inv.setQtyClosing(tonToi);

            // qtySoldReported = tonHomTruoc + banhSang - huy - tonToi
            BigDecimal soldReported = tonHomTruoc.add(banhSang)
                .subtract(huy)
                .subtract(tonToi)
                .max(BigDecimal.ZERO);
            inv.setQtySoldReported(soldReported);

            dailyInventoryRepository.save(inv);

            // So sánh với dữ liệu POS (đã được import lúc 21:30)
            BigDecimal soldPos = inv.getQtySoldDerived() != null
                ? inv.getQtySoldDerived()
                : BigDecimal.ZERO;

            // system_closing = tonHomTruoc + banhSang - soldPos - huy
            BigDecimal systemClosing = tonHomTruoc.add(banhSang)
                .subtract(soldPos)
                .subtract(huy)
                .max(BigDecimal.ZERO);

            BigDecimal diff = systemClosing.subtract(tonToi);

            results.add(new CompareResult(
                row.masp(),
                row.tenBanh(),
                tonHomTruoc,
                banhSang,
                soldPos,
                soldReported,
                huy,
                tonToi,
                systemClosing,
                diff,
                diff.abs().compareTo(BigDecimal.ONE) > 0 ? "CHÊNH LỆCH" : "OK"
            ));
        }

        // 3. Xuất TongHop
        Path tongHopPath = tongHopExcelService.generate(processDate, results);
        log.info("✓ Xuất TongHop: {}", tongHopPath);

        // 4. Xuất BanhRaNgay ngày mai
        LocalDate tomorrow = processDate.plusDays(1);
        Path banhRaNgayPath = banhRaNgayGenerator.generate(processDate, tomorrow);
        log.info("✓ Xuất BanhRaNgay {}: {}", tomorrow, banhRaNgayPath);

        long errors = results.stream().filter(r -> "CHÊNH LỆCH".equals(r.status())).count();
        log.info("✓ BaoCaoNgay xong | rows={} | chênh lệch={}", rows.size(), errors);

        return new ProcessResult(
            processDate,
            rows.size(),
            (int) errors,
            tongHopPath,
            banhRaNgayPath,
            results
        );
    }

    // ── Parse sheet SX ────────────────────────────────────────

    private List<ReportRow> parseSheetSX(Path filePath, LocalDate processDate) throws Exception {
        List<ReportRow> rows = new ArrayList<>();

        try (var fis = new java.io.FileInputStream(filePath.toFile());
             Workbook wb = new XSSFWorkbook(fis)) {

            // Tìm sheet SX (index 0 hoặc theo tên)
            Sheet sheet = null;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = wb.getSheetName(i);
                if ("SX".equalsIgnoreCase(name) || name.toUpperCase().contains("SX")) {
                    sheet = wb.getSheetAt(i);
                    break;
                }
            }
            if (sheet == null) sheet = wb.getSheetAt(0);

            // Tìm dòng data (bỏ qua 2 dòng header)
            int dataStart = findDataStart(sheet);
            if (dataStart < 0) return rows;

            for (int i = dataStart; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String masp = getCellString(row, COL_MA_SP);
                if (masp == null || masp.isBlank()) continue;
                if (!masp.toUpperCase().startsWith("SP")) continue; // bỏ dòng tổng

                String tenBanh       = getCellString(row, COL_TEN_BANH);
                BigDecimal tonHomTruoc = getCellDecimalOrZero(row, COL_TON_HOM_TRUOC);
                BigDecimal banhSang    = getCellDecimalOrZero(row, COL_BANH_SANG);
                BigDecimal tonToi      = getCellDecimalOrZero(row, COL_TON_TOI);
                BigDecimal huy         = getCellDecimalOrZero(row, COL_HUY);

                rows.add(new ReportRow(masp, tenBanh, tonHomTruoc, banhSang, tonToi, huy));
            }
        }

        return rows;
    }

    /**
     * Tìm dòng bắt đầu data.
     * Nhận diện: dòng tiếp theo sau dòng có "Tồn hôm trước" hoặc "tồn hôm trước"
     */
    private int findDataStart(Sheet sheet) {
        for (int i = 0; i <= Math.min(5, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = getCellStringDirect(cell);
                if (val != null && val.toLowerCase().contains("tồn hôm trước")) {
                    return i + 1; // data bắt đầu sau dòng sub-header
                }
            }
        }
        return 2; // fallback: bỏ 2 dòng header
    }

    // ── Cell helpers ──────────────────────────────────────────

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return getCellStringDirect(cell);
    }

    private String getCellStringDirect(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    private BigDecimal getCellDecimalOrZero(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return BigDecimal.ZERO;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> {
                try { yield new BigDecimal(cell.getStringCellValue().replaceAll("[^0-9.]", "")); }
                catch (NumberFormatException e) { yield BigDecimal.ZERO; }
            }
            default -> BigDecimal.ZERO;
        };
    }

    // ── Records ───────────────────────────────────────────────

    private record ReportRow(
        String     masp,
        String     tenBanh,
        BigDecimal tonHomTruoc,
        BigDecimal banhSang,
        BigDecimal tonToi,
        BigDecimal huy
    ) {}

    public record CompareResult(
        String     masp,
        String     tenBanh,
        BigDecimal tonHomTruoc,
        BigDecimal banhSang,
        BigDecimal soldPos,         // Từ POS (đã import lúc 21:30)
        BigDecimal soldReported,    // NV báo (tính từ BaoCaoNgay)
        BigDecimal huy,
        BigDecimal tonToi,          // NV đếm thực tế
        BigDecimal systemClosing,   // Hệ thống tính
        BigDecimal diff,            // systemClosing - tonToi
        String     status           // OK | CHÊNH LỆCH
    ) {
        static CompareResult notFound(String masp, String tenBanh) {
            return new CompareResult(
                masp, tenBanh,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, "NOT_FOUND"
            );
        }
        public boolean hasDiscrepancy() {
            return "CHÊNH LỆCH".equals(status);
        }
    }

    public record ProcessResult(
        LocalDate           processDate,
        int                 rowsProcessed,
        int                 discrepancyCount,
        Path                tongHopFile,
        Path                banhRaNgayFile,
        List<CompareResult> details
    ) {}
}
