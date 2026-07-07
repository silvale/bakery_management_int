package com.bakery.api.batch.services;

import com.bakery.api.framework.dtos.ReconcileResultResponse;
import com.bakery.api.framework.dtos.ReconcileResultResponse.*;
import com.bakery.api.batch.dto.*;
//import com.bakery.api.framework.services.ReportExcelService;
import com.bakery.api.batch.processor.DailyImportProcessor;
import com.bakery.api.batch.processor.ReconcileProcessor;
import com.bakery.api.batch.reader.*;
import com.bakery.api.batch.util.ErrorRowCollector;
import com.bakery.api.framework.*;
import com.bakery.api.framework.enums.BatchRunType;
import com.bakery.api.framework.enums.BatchStatus;
import com.bakery.api.framework.enums.BranchType;
import com.bakery.api.framework.enums.FileType;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.bakery.api.batch.entities.BatchRun;
import com.bakery.api.batch.entities.FileImportLog;
import com.bakery.api.batch.repositories.BatchRunRepository;
import com.bakery.api.batch.repositories.FileImportLogRepository;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import com.bakery.api.modules.sales.entities.DailyReconcile;
import com.bakery.api.modules.sales.repositories.DailyReconcileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchApiService {

    private final BranchRepository          branchRepository;
    private final BatchRunRepository         batchRunRepository;
    private final FileImportLogRepository    fileImportLogRepository;
    private final DailyReconcileRepository   dailyReconcileRepository;
    private final ProductRepository          productRepository;
    private final DailyImportProcessor       importProcessor;
//    private final ReportExcelService          reportExcelService;
    private final ReconcileProcessor         reconcileProcessor;

    @Value("${bakery.batch.input-dir:./batch-input}")
    private String inputDir;

    @Value("${bakery.batch.output-dir:./batch-output}")
    private String outputDir;

    private static final String FILE_BANH_RA_NGAY = "BanhRaNgay.xlsx";
    private static final String FILE_XUAT_RA       = "XuatRa.xlsx";
    private static final String FILE_BAO_CAO_NGAY  = "BaoCaoNgay.xlsx";
    private static final String FILE_POS           = "BigProductBySaleByCat.xlsx";

    // -------------------------------------------------------
    //  Chạy batch và trả về kết quả ngay
    // -------------------------------------------------------
    @Transactional
    public ReconcileResultResponse runAndReconcile(LocalDate processDate, String triggeredBy) {
        log.info("API trigger batch | Ngày: {} | By: {}", processDate, triggeredBy);

        // Load branches
        Branch kitchenBranch = branchRepository.findByBranchType(BranchType.KHO_BEP)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy Kho Bếp (KHO_BEP)"));
        Branch shopBranch = branchRepository.findByBranchType(BranchType.SHOP)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy Cửa hàng (SHOP)"));

        // Tạo BatchRun
        boolean isRerun = batchRunRepository
            .existsByProcessDateAndRunType(processDate, BatchRunType.MANUAL);

        BatchRun batchRun = BatchRun.builder()
            .runType(BatchRunType.MANUAL)
            .processDate(processDate)
            .isRerun(isRerun)
            .status(BatchStatus.RUNNING)
            .triggeredBy(triggeredBy)
            .startedAt(OffsetDateTime.now())
            .build();
        batchRunRepository.save(batchRun);

        boolean hasError = false;

        // Step 1: BanhRaNgay
        hasError |= !runStep(batchRun, FileType.PRODUCTION_REQUEST, FILE_BANH_RA_NGAY,
            (path, col) -> {
                List<ProductionRequestRow> rows =
                    ProductionRequestReader.read(path, col, processDate);
                importProcessor.processProductionRequest(rows, kitchenBranch, col);
            });

        // Step 2: XuatRa
        hasError |= !runStep(batchRun, FileType.PRODUCTION_ACTUAL, FILE_XUAT_RA,
            (path, col) -> {
                List<ProductionActualRow> rows =
                    ProductionActualReader.read(path, col, processDate);
                importProcessor.processProductionActual(rows, kitchenBranch, shopBranch, col);
            });

        // Step 3: BaoCaoNgay
        hasError |= !runStep(batchRun, FileType.DAILY_INVENTORY, FILE_BAO_CAO_NGAY,
            (path, col) -> {
                List<DailyInventoryRow> rows =
                    DailyInventoryReader.read(path, col, processDate);
                importProcessor.processDailyInventory(rows, shopBranch, kitchenBranch, col);
            });

        // Step 4: POS
        hasError |= !runStep(batchRun, FileType.POS_EXPORT, FILE_POS,
            (path, col) -> {
                List<PosTransactionRow> rows = PosExportReader.read(path, col, processDate);
                importProcessor.processPosExport(rows, shopBranch, col);
            });

        // Step 5: Reconcile
        try {
            reconcileProcessor.reconcile(processDate, kitchenBranch, shopBranch);
        } catch (Exception e) {
            log.error("Reconcile failed: {}", e.getMessage(), e);
            hasError = true;
            batchRun.setErrorSummary("Reconcile failed: " + e.getMessage());
        }

        batchRun.setStatus(hasError ? BatchStatus.PARTIAL : BatchStatus.COMPLETED);
        batchRun.setFinishedAt(OffsetDateTime.now());
        batchRunRepository.save(batchRun);

        // Step 6: Auto export Excel
//        autoExportExcel(processDate);

        // Build response
        return buildResponse(batchRun, processDate, shopBranch);
    }

    // -------------------------------------------------------
    //  Lấy kết quả reconcile đã có trong DB (không chạy lại)
    // -------------------------------------------------------
    @Transactional(readOnly = true)
    public ReconcileResultResponse getResult(LocalDate date) {
        Branch shopBranch = branchRepository.findByBranchType(BranchType.SHOP)
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy Cửa hàng (SHOP)"));

        BatchRun batchRun = batchRunRepository
            .findLatestByProcessDateAndRunType(date, BatchRunType.MANUAL)
            .orElseThrow(() -> new IllegalStateException("Chưa có batch run cho ngày " + date));

        return buildResponse(batchRun, date, shopBranch);
    }

    // -------------------------------------------------------
    //  Build response từ DB
    // -------------------------------------------------------
    private ReconcileResultResponse buildResponse(BatchRun batchRun, LocalDate date, Branch shopBranch) {

        List<DailyReconcile> reconciles = dailyReconcileRepository
            .findAllByBranchIdAndReconDate(shopBranch.getId(), date);

        List<FileImportLog> fileLogs = fileImportLogRepository
            .findAllByBatchRunId(batchRun.getId());

        // Summary
        BigDecimal totalRevenue        = sum(reconciles, r -> r.getRevenue());
        BigDecimal totalSalesCost      = sum(reconciles, r -> r.getSalesCost());
        BigDecimal totalCancelledCost  = sum(reconciles, r -> r.getCancelledCost());
        BigDecimal totalGrossProfit    = sum(reconciles, r -> r.getGrossProfit());

        long okCount          = reconciles.stream().filter(r -> "OK".equals(r.getOverallStatus().name())).count();
        long discrepancyCount = reconciles.stream().filter(r -> "DISCREPANCY".equals(r.getOverallStatus().name())).count();
        long pendingCount     = reconciles.stream().filter(r -> "PENDING".equals(r.getOverallStatus().name())).count();

        Summary summary = Summary.builder()
            .totalProducts((int) reconciles.size())
            .okCount((int) okCount)
            .discrepancyCount((int) discrepancyCount)
            .pendingCount((int) pendingCount)
            .totalRevenue(totalRevenue)
            .totalSalesCost(totalSalesCost)
            .totalCancelledCost(totalCancelledCost)
            .totalGrossProfit(totalGrossProfit)
            .build();

        // Product details
        List<ProductReconcile> products = reconciles.stream()
            .map(r -> ProductReconcile.builder()
                .productCode(r.getProduct().getCode())
                .productName(r.getProduct().getName())
                .unit(r.getProduct().getUnit())
                // Tầng 1
                .qtyRequested(r.getQtyRequested())
                .qtyProduced(r.getQtyProduced())
                .productionDiff(r.getProductionVsOrderDiff())
                .productionStatus(r.getProductionVsOrderStatus().name())
                // Tầng 2
                .qtySent(r.getQtySent())
                .qtyReceived(r.getQtyReceived())
                .deliveryDiff(r.getDeliveryVsReceiptDiff())
                .deliveryStatus(r.getDeliveryVsReceiptStatus().name())
                // Tầng 3
                .qtySoldPos(r.getQtySoldPos())
                .qtySoldDerived(r.getQtySoldDerived())
                .qtyOpening(r.getQtyOpening())
                .qtyCancelled(r.getQtyCancelled())
                .qtyClosing(r.getQtyClosing())
                .posDiff(r.getPosVsInventoryDiff())
                .posStatus(r.getPosVsInventoryStatus().name())
                // Cost
                .costPerUnit(r.getCostPerUnit())
                .unitPrice(r.getUnitPrice())
                .revenue(r.getRevenue())
                .salesCost(r.getSalesCost())
                .cancelledCost(r.getCancelledCost())
                .grossProfit(r.getGrossProfit())
                // Overall
                .overallStatus(r.getOverallStatus().name())
                .discrepancyNote(r.getDiscrepancyNote())
                .build())
            .collect(Collectors.toList());

        // File logs
        List<FileImportResult> fileImports = fileLogs.stream()
            .map(f -> FileImportResult.builder()
                .fileName(f.getFileName())
                .fileType(f.getFileType().name())
                .status(f.getStatus().name())
                .rowsTotal(f.getRowsTotal())
                .rowsOk(f.getRowsOk())
                .rowsError(f.getRowsError())
                .errorDetail(f.getErrorDetail())
                .errorRowIndices(f.getErrorRowIndices())
                .build())
            .collect(Collectors.toList());

        return ReconcileResultResponse.builder()
            .reconDate(date)
            .batchRunId(batchRun.getId().toString())
            .batchStatus(batchRun.getStatus().name())
            .isRerun(batchRun.getIsRerun())
            .summary(summary)
            .products(products)
            .fileImports(fileImports)
            .build();
    }

    // -------------------------------------------------------
    //  Step runner
    // -------------------------------------------------------
    private boolean runStep(BatchRun batchRun, FileType fileType,
                            String fileName, StepRunner runner) {
        String filePath = inputDir + "/" + fileName;
        ErrorRowCollector collector = new ErrorRowCollector();

        FileImportLog logEntry = FileImportLog.builder()
            .batchRun(batchRun)
            .fileName(fileName)
            .fileType(fileType)
            .status(BatchStatus.RUNNING)
            .importedAt(OffsetDateTime.now())
            .build();
        fileImportLogRepository.save(logEntry);

        try {
            runner.run(filePath, collector);

            logEntry.setStatus(collector.hasErrors() ? BatchStatus.PARTIAL : BatchStatus.SUCCESS);
            logEntry.setRowsTotal(collector.getTotalRows());
            logEntry.setRowsOk(collector.getSuccessRows());
            logEntry.setRowsError(collector.getErrorRows());
            logEntry.setErrorDetail(collector.buildErrorSummary());
            logEntry.setErrorRowIndices(collector.getErrors());
            fileImportLogRepository.save(logEntry);
            return true;

        } catch (Exception e) {
            log.error("File {} failed: {}", fileName, e.getMessage(), e);
            logEntry.setStatus(BatchStatus.FAILED);
            logEntry.setErrorDetail(e.getMessage());
            fileImportLogRepository.save(logEntry);
            return false;
        }
    }

    @FunctionalInterface
    private interface StepRunner {
        void run(String filePath, ErrorRowCollector collector) throws Exception;
    }

    private BigDecimal sum(List<DailyReconcile> list,
                           java.util.function.Function<DailyReconcile, BigDecimal> getter) {
        return list.stream()
            .map(getter)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // -------------------------------------------------------
    //  Auto export Excel sau khi reconcile xong
    // -------------------------------------------------------
//    private void autoExportExcel(LocalDate date) {
//        try {
//            // Tạo thư mục output theo ngày: batch-output/2026/04/
//            String dateFolder = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM"));
//            String filename   = "BaoCaoNgay_" +
//                date.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";
//
//            Path outputPath = Paths.get(outputDir, dateFolder);
//            Files.createDirectories(outputPath);
//
//            Path filePath = outputPath.resolve(filename);
//
//            byte[] excelBytes = reportExcelService.exportDailyReport(date);
//
//            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
//                fos.write(excelBytes);
//            }
//
//            log.info("✓ Export Excel thành công: {}", filePath.toAbsolutePath());
//
//        } catch (Exception e) {
//            // Không throw — lỗi export không nên dừng toàn bộ batch
//            log.error("✗ Export Excel thất bại: {}", e.getMessage(), e);
//        }
//    }

}