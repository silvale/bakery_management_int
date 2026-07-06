package com.bakery.batch.job;

import com.bakery.batch.dto.*;
import com.bakery.batch.processor.DailyImportProcessor;
import com.bakery.batch.processor.ReconcileProcessor;
import com.bakery.batch.reader.*;
import com.bakery.batch.util.ErrorRowCollector;
import com.bakery.common.entity.BatchRun;
import com.bakery.common.entity.Branch;
import com.bakery.common.entity.FileImportLog;
import com.bakery.common.entity.enums.BatchRunType;
import com.bakery.common.entity.enums.BatchStatus;
import com.bakery.common.entity.enums.BranchType;
import com.bakery.common.entity.enums.FileType;
import com.bakery.common.repository.BatchRunRepository;
import com.bakery.common.repository.BranchRepository;
import com.bakery.common.repository.FileImportLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Điều phối toàn bộ Daily Batch Job.
 *
 * Flow:
 *   1. Tạo BatchRun record
 *   2. Đọc + import file BanhRaNgay.xlsx
 *   3. Đọc + import file XuatRa.xlsx
 *   4. Đọc + import file BaoCaoNgay.xlsx
 *   5. Đọc + import file BigProductBySaleByCat.xlsx
 *   6. Chạy Reconcile
 *   7. Cập nhật BatchRun status
 *
 * Strategy: "fail-soft" — 1 file lỗi không dừng các file còn lại.
 * BatchRun.status = PARTIAL nếu có file lỗi, COMPLETED nếu tất cả OK.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyBatchJobService {

    private final BranchRepository       branchRepository;
    private final BatchRunRepository     batchRunRepository;
    private final FileImportLogRepository fileImportLogRepository;
    private final DailyImportProcessor   importProcessor;
    private final ReconcileProcessor     reconcileProcessor;

    @Value("${bakery.batch.input-dir:./batch-input}")
    private String inputDir;

    // File name conventions
    private static final String FILE_BANH_RA_NGAY  = "BanhRaNgay.xlsx";
    private static final String FILE_XUAT_RA        = "XuatRa.xlsx";
    private static final String FILE_BAO_CAO_NGAY   = "BaoCaoNgay.xlsx";
    private static final String FILE_POS            = "BigProductBySaleByCat.xlsx";

    /**
     * Entry point: trigger từ scheduler hoặc manual API.
     *
     * @param processDate  Ngày cần xử lý
     * @param triggeredBy  Username hoặc "SCHEDULER"
     * @param runType      WEEKLY_AUTO | MANUAL
     */
    public BatchRun run(LocalDate processDate, String triggeredBy, BatchRunType runType) {
        log.info("╔══════════════════════════════════════╗");
        log.info("║  Daily Batch Job bắt đầu             ║");
        log.info("║  Ngày: {} | By: {}      ║", processDate, triggeredBy);
        log.info("╚══════════════════════════════════════╝");

        // 1. Tạo BatchRun
        boolean isRerun = batchRunRepository.existsByProcessDateAndRunType(processDate, runType);
        BatchRun batchRun = BatchRun.builder()
                .runType(runType)
                .processDate(processDate)
                .isRerun(isRerun)
                .status(BatchStatus.RUNNING)
                .triggeredBy(triggeredBy)
                .startedAt(OffsetDateTime.now())
                .build();
        batchRunRepository.save(batchRun);

        if (isRerun) {
            log.warn("⚠ Re-run phát hiện: ngày {} đã có dữ liệu. Overwrite.", processDate);
        }

        // 2. Load branches
        Branch kitchenBranch = branchRepository.findByBranchType(BranchType.KHO_BEP)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Kho Bếp (KHO_BEP)"));
        Branch shopBranch = branchRepository.findByBranchType(BranchType.SHOP)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy chi nhánh cửa hàng (SHOP)"));

        boolean hasError = false;

        // ─────────────────────────────────────────────
        // Step 1: BanhRaNgay.xlsx
        // ─────────────────────────────────────────────
        hasError |= !runStep(
                batchRun, FileType.PRODUCTION_REQUEST,
                FILE_BANH_RA_NGAY, processDate,
                (filePath, collector) -> {
                    List<ProductionRequestRow> rows =
                            ProductionRequestReader.read(filePath, collector, processDate);
                    importProcessor.processProductionRequest(rows, kitchenBranch, collector);
                }
        );

        // ─────────────────────────────────────────────
        // Step 2: XuatRa.xlsx
        // ─────────────────────────────────────────────
        hasError |= !runStep(
                batchRun, FileType.PRODUCTION_ACTUAL,
                FILE_XUAT_RA, processDate,
                (filePath, collector) -> {
                    List<ProductionActualRow> rows =
                            ProductionActualReader.read(filePath, collector, processDate);
                    importProcessor.processProductionActual(rows, kitchenBranch, shopBranch, collector);
                }
        );

        // ─────────────────────────────────────────────
        // Step 3: BaoCaoNgay.xlsx
        // ─────────────────────────────────────────────
        hasError |= !runStep(
                batchRun, FileType.DAILY_INVENTORY,
                FILE_BAO_CAO_NGAY, processDate,
                (filePath, collector) -> {
                    List<DailyInventoryRow> rows =
                            DailyInventoryReader.read(filePath, collector, processDate);
                    importProcessor.processDailyInventory(rows, shopBranch, kitchenBranch, collector);
                }
        );

        // ─────────────────────────────────────────────
        // Step 4: POS Export
        // ─────────────────────────────────────────────
        hasError |= !runStep(
                batchRun, FileType.POS_EXPORT,
                FILE_POS, processDate,
                (filePath, collector) -> {
                    List<PosTransactionRow> rows =
                            PosExportReader.read(filePath, collector, processDate);
                    importProcessor.processPosExport(rows, shopBranch, collector);
                }
        );

        // ─────────────────────────────────────────────
        // Step 5: Reconcile (chỉ chạy nếu không có lỗi nghiêm trọng)
        // ─────────────────────────────────────────────
        try {
            int reconCount = reconcileProcessor.reconcile(processDate, kitchenBranch, shopBranch);
            log.info("✓ Reconcile hoàn thành: {} dòng", reconCount);
        } catch (Exception e) {
            log.error("✗ Reconcile thất bại: {}", e.getMessage(), e);
            hasError = true;
            batchRun.setErrorSummary("Reconcile failed: " + e.getMessage());
        }

        // 7. Cập nhật BatchRun status
        batchRun.setStatus(hasError ? BatchStatus.PARTIAL : BatchStatus.COMPLETED);
        batchRun.setFinishedAt(OffsetDateTime.now());
        batchRunRepository.save(batchRun);

        log.info("╔══════════════════════════════════════╗");
        log.info("║  Daily Batch Job KẾT THÚC            ║");
        log.info("║  Status: {}                    ║", batchRun.getStatus());
        log.info("╚══════════════════════════════════════╝");

        return batchRun;
    }

    // -------------------------------------------------------
    // Step runner: đọc file + xử lý + ghi FileImportLog
    // -------------------------------------------------------

    private boolean runStep(
            BatchRun batchRun,
            FileType fileType,
            String fileName,
            LocalDate processDate,
            StepRunner runner) {

        String filePath = inputDir + "/" + fileName;
        ErrorRowCollector collector = new ErrorRowCollector();

        FileImportLog log_ = FileImportLog.builder()
                .batchRun(batchRun)
                .fileName(fileName)
                .fileType(fileType)
                .status(BatchStatus.RUNNING)
                .importedAt(OffsetDateTime.now())
                .build();
        fileImportLogRepository.save(log_);

        try {
            log.info("▶ Đọc file: {}", fileName);
            runner.run(filePath, collector);

            log_.setStatus(collector.hasErrors() ? BatchStatus.PARTIAL : BatchStatus.SUCCESS);
            log_.setRowsTotal(collector.getTotalRows());
            log_.setRowsOk(collector.getSuccessRows());
            log_.setRowsError(collector.getErrorRows());
            log_.setErrorDetail(collector.buildErrorSummary());
            log_.setErrorRowIndices(collector.getErrors());
            fileImportLogRepository.save(log_);

            log.info("✓ {} | OK: {} | Lỗi: {}",
                    fileName, collector.getSuccessRows(), collector.getErrorRows());
            return true;

        } catch (Exception e) {
            log.error("✗ {} thất bại: {}", fileName, e.getMessage(), e);
            log_.setStatus(BatchStatus.FAILED);
            log_.setErrorDetail(e.getMessage());
            fileImportLogRepository.save(log_);
            return false;
        }
    }

    // Functional interface cho step runner
    @FunctionalInterface
    private interface StepRunner {
        void run(String filePath, ErrorRowCollector collector) throws Exception;
    }
}