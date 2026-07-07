package com.bakery.api.batch.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.enums.LotStatus;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.modules.masterdata.entities.ProductMapping;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.ProductExpiryConfigRepository;
import com.bakery.api.modules.masterdata.repositories.ProductMappingRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import com.bakery.api.modules.production.entities.ProductionLot;
import com.bakery.api.modules.production.repositories.ProductionLotRepository;
import com.bakery.api.modules.sales.entities.PosTransaction;
import com.bakery.api.modules.sales.repositories.PosTransactionRepository;

/**
 * Xử lý file POS Excel cuối ngày (~21:30).
 *
 * Input:  /input/pos/BaoCaoPos_{date}.xlsx
 *         Cột: Mã hàng (EX_CODE) | Tên hàng | SL bán | Doanh thu | SL trả | Giá trị trả | Doanh thu thuần
 *
 * Output:
 *   1. Cập nhật ProductionLot.qtySold theo IN_CODE + ngày SX
 *   2. Xuất /output/huy-banh/HuyBanh_{date}.xlsx
 *
 * Gọi bởi: PosFileWatcherService (cron 21h)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosFileProcessorService {

    private final ExCodeDecoderService          exCodeDecoder;
    private final ProductionLotRepository       productionLotRepository;
    private final BranchRepository              branchRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final PosTransactionRepository      posTransactionRepository;
    private final ProductRepository             productRepository;
    private final ProductMappingRepository      productMappingRepository;

    // ── Cột trong file POS ────────────────────────────────────
    // Cột trong file POS thực tế (0-indexed):
    // Col 0: (trống)  Col 1: Mã hàng  Col 2: Tên hàng  Col 3-4: (trống)
    // Col 5: SL bán   Col 6: Doanh thu  Col 7: SL trả  Col 8: Giá trị trả
    // Col 9: (trống)  Col 10: DT thuần
    private static final int COL_MA_HANG    = 1;
    private static final int COL_TEN_HANG   = 2;
    private static final int COL_SL_BAN     = 5;
    private static final int COL_DOANH_THU  = 6;
    private static final int COL_SL_TRA     = 7;
    private static final int COL_GT_TRA     = 8;
    private static final int COL_DT_THUAN   = 10;

    // ── Process ───────────────────────────────────────────────

    /**
     * Xử lý file POS Excel.
     *
     * @param filePath  Đường dẫn file POS
     * @param processDate Ngày xử lý (ngày file POS)
     * @return ProcessResult
     */
    @Transactional
    public ProcessResult process(Path filePath, LocalDate processDate) throws Exception {
        log.info("=== POS Processor | file={} | date={} ===", filePath.getFileName(), processDate);

        Branch shopBranch = branchRepository.findByBranchType(BranchType.SHOP)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy chi nhánh cửa hàng (SHOP)"));

        // 1. Parse file POS
        List<PosRow> rows = parsePosFile(filePath, processDate);
        log.info("POS parse: {} dòng hợp lệ", rows.size());

        // 2. Group by IN_CODE + productionDate → tổng SL bán
        Map<String, SalesAgg> aggMap = aggregateSales(rows);

        // 3. Cập nhật ProductionLot.qtySold
        int updatedLots = 0;
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, SalesAgg> entry : aggMap.entrySet()) {
            String key = entry.getKey(); // "INCODE_yyyy-MM-dd"
            SalesAgg agg = entry.getValue();

            List<ProductionLot> lots = productionLotRepository
                .findByProductCodeAndProductionDate(agg.inCode(), agg.productionDate(), shopBranch.getId());

            if (lots.isEmpty()) {
                // Tạo lot ảo nếu chưa có (bếp chưa khai báo)
                log.warn("Không có ProductionLot cho {} ngày {} → bỏ qua cập nhật qtySold",
                    agg.inCode(), agg.productionDate());
                warnings.add("Thiếu lot: " + agg.inCode() + " ngày " + agg.productionDate());
                continue;
            }

            // Cộng dồn qtySold vào lot đầu tiên còn hàng (FIFO)
            BigDecimal remaining = agg.qtySold();
            for (ProductionLot lot : lots) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal canSell = lot.getQtyRemaining() != null
                    ? lot.getQtyRemaining().min(remaining)
                    : remaining;

                lot.setQtySold(lot.getQtySold().add(canSell));
                if (lot.getQtyRemaining() != null
                        && lot.getQtyRemaining().subtract(canSell).compareTo(BigDecimal.ZERO) <= 0) {
                    lot.setStatus(LotStatus.SOLD_OUT);
                }
                productionLotRepository.save(lot);
                remaining = remaining.subtract(canSell);
                updatedLots++;
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                warnings.add(String.format("SL bán %s vượt tồn kho lot: %s ngày %s (dư %s)",
                    agg.qtySold(), agg.inCode(), agg.productionDate(), remaining));
            }
        }

        // 4. Upsert PosTransaction — tổng bán mỗi sản phẩm trong ngày
        //    (aggregate across all productionDates → 1 row per product per day)
        upsertPosTransactions(aggMap, shopBranch, processDate, filePath);

        // V12: HuyBanh không còn xuất Excel — xử lý qua InventoryWriteOff UI
        log.info("✓ POS Processor xong | rows={} | updatedLots={} | warnings={}",
            rows.size(), updatedLots, warnings.size());

        return new ProcessResult(
            processDate,
            rows.size(),
            aggMap.size(),
            updatedLots,
            warnings
        );
    }

    // ── Upsert PosTransaction ─────────────────────────────────

    /**
     * Gộp tổng bán theo IN_CODE (bỏ qua productionDate) → upsert vào pos_transaction.
     * Đây là nguồn dữ liệu "Bán POS" trong TongHop.
     */
    private void upsertPosTransactions(Map<String, SalesAgg> aggMap,
                                        Branch shopBranch,
                                        LocalDate processDate,
                                        Path filePath) {
        // Group by inCode → tổng cộng qtySold + netRevenue
        Map<String, BigDecimal> soldByCode    = new LinkedHashMap<>();
        Map<String, BigDecimal> revenueByCode = new LinkedHashMap<>();

        for (SalesAgg agg : aggMap.values()) {
            soldByCode.merge(agg.inCode(), agg.qtySold(), BigDecimal::add);
            revenueByCode.merge(agg.inCode(), agg.netRevenue(), BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> entry : soldByCode.entrySet()) {
            String     inCode     = entry.getKey();
            BigDecimal qtySold    = entry.getValue();
            BigDecimal revenue    = revenueByCode.getOrDefault(inCode, BigDecimal.ZERO);
            BigDecimal unitPrice  = qtySold.compareTo(BigDecimal.ZERO) > 0
                ? revenue.divide(qtySold, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            productRepository.findByCode(inCode).ifPresentOrElse(product -> {
                posTransactionRepository
                    .findByBranchIdAndProductIdAndTransactionDate(
                        shopBranch.getId(), product.getId(), processDate)
                    .ifPresentOrElse(tx -> {
                        tx.setQtySold(qtySold);
                        tx.setUnitPrice(unitPrice);
                        tx.setRevenue(revenue);
                        posTransactionRepository.save(tx);
                    }, () -> posTransactionRepository.save(
                        PosTransaction.builder()
                            .branch(shopBranch)
                            .product(product)
                            .transactionDate(processDate)
                            .qtySold(qtySold)
                            .unitPrice(unitPrice)
                            .revenue(revenue)
                            .sourceFile(filePath.getFileName().toString())
                            .build()
                    ));
                log.debug("  PosTransaction upsert: {} qtySold={}", inCode, qtySold);
            }, () -> log.warn("  PosTransaction: không tìm thấy product code={}", inCode));
        }

        log.info("✓ Upsert PosTransaction: {} sản phẩm", soldByCode.size());
    }

    // ── Parse POS Excel ───────────────────────────────────────

    private List<PosRow> parsePosFile(Path filePath, LocalDate processDate) throws Exception {
        List<PosRow> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            int dataStartRow = findDataStartRow(sheet);
            if (dataStartRow < 0) {
                log.warn("Không tìm thấy header trong file POS: {}", filePath);
                return rows;
            }

            int skipped = 0;
            List<String> skippedSkus = new ArrayList<>();
            for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String maHang = getCellString(row, COL_MA_HANG);
                if (maHang == null || maHang.isBlank()) continue;
                if (maHang.startsWith("SL") || maHang.startsWith("Tổng")) continue;

                BigDecimal slBan = getCellDecimal(row, COL_SL_BAN);
                if (slBan == null || slBan.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 1. Tìm sản phẩm qua product_mapping (exact SKU → IN_CODE)
                Optional<ProductMapping> mappingOpt =
                    productMappingRepository.findWithProductBySkuCode(maHang);

                if (mappingOpt.isEmpty()) {
                    log.warn("  Row {}: SKU '{}' không có trong product_mapping → bỏ qua (thêm mapping nếu thiếu)", i + 1, maHang);
                    skipped++;
                    skippedSkus.add(maHang);
                    continue;
                }
                Product product = mappingOpt.get().getProduct();

                // 2. Lấy productionDate từ ExCodeDecoder (dùng cho FIFO lot)
                //    Nếu decode thất bại (SP..., format lạ) → fallback về processDate
                LocalDate productionDate;
                ExCodeDecoderService.DecodeResult decoded = exCodeDecoder.decode(maHang, processDate);
                if (decoded != null) {
                    productionDate = decoded.productionDate();
                } else {
                    productionDate = processDate;
                    log.debug("  Row {}: Không decode được ngày SX cho '{}' → dùng processDate", i + 1, maHang);
                }

                BigDecimal doanhThu  = getCellDecimal(row, COL_DOANH_THU);
                BigDecimal slTra     = getCellDecimal(row, COL_SL_TRA);
                BigDecimal giaTriTra = getCellDecimal(row, COL_GT_TRA);
                BigDecimal dtThuan   = getCellDecimal(row, COL_DT_THUAN);

                rows.add(new PosRow(
                    maHang,
                    getCellString(row, COL_TEN_HANG),
                    product,
                    productionDate,
                    slBan,
                    doanhThu   != null ? doanhThu   : BigDecimal.ZERO,
                    slTra      != null ? slTra      : BigDecimal.ZERO,
                    giaTriTra  != null ? giaTriTra  : BigDecimal.ZERO,
                    dtThuan    != null ? dtThuan    : BigDecimal.ZERO
                ));
            }

            if (skipped > 0) {
                log.warn("  POS parse: {} dòng bỏ qua vì SKU chưa có trong product_mapping: {}",
                    skipped, skippedSkus);
            }
        }

        return rows;
    }

    /**
     * Tìm dòng bắt đầu data (sau dòng header).
     * Nhận diện: dòng có "Mã hàng" hoặc "Mã" ở cột đầu.
     */
    private int findDataStartRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(15, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            // Tìm "Mã hàng" ở bất kỳ cột nào trong 3 cột đầu
            for (int c = 0; c <= 2; c++) {
                String cell = getCellString(row, c);
                if (cell != null && (cell.contains("Mã hàng") || cell.equalsIgnoreCase("Mã"))) {
                    return i + 1; // data bắt đầu từ dòng tiếp theo
                }
            }
        }
        return -1;
    }

    // ── Aggregate sales ───────────────────────────────────────

    /**
     * Gộp POS rows theo IN_CODE + productionDate.
     * Key: "{inCode}_{productionDate}"
     */
    private Map<String, SalesAgg> aggregateSales(List<PosRow> rows) {
        Map<String, SalesAgg> map = new LinkedHashMap<>();

        for (PosRow row : rows) {
            String inCode = row.product().getCode();       // từ product_mapping
            LocalDate prodDate = row.productionDate();     // từ ExCodeDecoder / processDate
            String key = inCode + "_" + prodDate;

            SalesAgg existing = map.get(key);
            if (existing == null) {
                map.put(key, new SalesAgg(
                    inCode, prodDate,
                    row.slBan(),
                    row.doanhThu(),
                    row.slTra(),
                    row.dtThuan()
                ));
            } else {
                map.put(key, new SalesAgg(
                    inCode, prodDate,
                    existing.qtySold().add(row.slBan()),
                    existing.revenue().add(row.doanhThu()),
                    existing.qtyReturn().add(row.slTra()),
                    existing.netRevenue().add(row.dtThuan())
                ));
            }
        }

        return map;
    }

    // ── Excel cell helpers ────────────────────────────────────

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> null;
        };
    }

    private BigDecimal getCellDecimal(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING  -> {
                try { yield new BigDecimal(cell.getStringCellValue().replaceAll("[^0-9.]", "")); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> null;
        };
    }

    // ── Records ───────────────────────────────────────────────

    private record PosRow(
        String     exCode,
        String     tenHang,
        Product    product,        // từ product_mapping
        LocalDate  productionDate, // từ ExCodeDecoder hoặc processDate
        BigDecimal                        slBan,
        BigDecimal                        doanhThu,
        BigDecimal                        slTra,
        BigDecimal                        giaTriTra,
        BigDecimal                        dtThuan
    ) {}

    private record SalesAgg(
        String     inCode,
        LocalDate  productionDate,
        BigDecimal qtySold,
        BigDecimal revenue,
        BigDecimal qtyReturn,
        BigDecimal netRevenue
    ) {}

    public record ProcessResult(
        LocalDate    processDate,
        int          rowsParsed,
        int          distinctProducts,
        int          lotsUpdated,
        List<String> warnings
    ) {
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}
