package com.bakery.api.service;

import com.bakery.common.entity.TxtImportLog;
import com.bakery.common.repository.TxtImportLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Report File Watcher Service.
 *
 * Cron: mỗi 5 phút trong khung 22:00 - 22:55.
 * Theo dõi thư mục /input/report/ tìm file BaoCaoNgay .xlsx
 * → xử lý đối chiếu 3 tầng → xuất TongHop + BanhRaNgay.
 *
 * Idempotency: MD5 checksum → skip nếu đã xử lý.
 * Dead-letter:  file lỗi → move sang /input/report/error/
 * Done:         file thành công → move sang /input/report/done/
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class ReportFileWatcherService {

    private final BaoCaoNgayProcessorService baoCaoNgayProcessorService;
    private final TxtImportLogRepository     txtImportLogRepository;

    @Value("${bakery.batch.input-dir:./batch-input}")
    private String inputDir;

    private static final String REPORT_SUBDIR = "report";

    /**
     * Cron: mỗi 5 phút, khung 22:00 đến 22:55
     * → 22:00, 22:05, 22:10, ... 22:55
     */
    @Scheduled(cron = "${bakery.watcher.report.cron:0 0/5 22 * * *}")
    public void watchReportDirectory() {
        Path watchDir = Paths.get(inputDir, REPORT_SUBDIR);
        if (!Files.exists(watchDir)) {
            log.debug("Report watch dir chưa tồn tại: {}", watchDir);
            return;
        }

        try (Stream<Path> files = Files.list(watchDir)) {
            List<Path> xlsxFiles = files
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                .filter(p -> Files.isRegularFile(p))
                .sorted()
                .toList();

            if (xlsxFiles.isEmpty()) {
                log.debug("Không có file BaoCaoNgay mới trong {}", watchDir);
                return;
            }

            for (Path file : xlsxFiles) {
                processReportFile(file);
            }

        } catch (IOException e) {
            log.error("Report watcher lỗi khi liệt kê thư mục: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý thủ công — dùng cho API trigger hoặc test.
     */
    public BaoCaoNgayProcessorService.ProcessResult processManual(Path filePath, LocalDate date) throws Exception {
        return processFileInternal(filePath, date);
    }

    // ── Internal ──────────────────────────────────────────────

    private void processReportFile(Path filePath) {
        log.info("Report watcher: phát hiện file {}", filePath.getFileName());
        try {
            processFileInternal(filePath, LocalDate.now());
        } catch (Exception e) {
            log.error("BaoCaoNgay xử lý thất bại [{}]: {}", filePath.getFileName(), e.getMessage(), e);
            moveToSubDir(filePath, "error");
        }
    }

    private BaoCaoNgayProcessorService.ProcessResult processFileInternal(Path filePath, LocalDate date) throws Exception {
        // 1. Tính MD5 để kiểm tra idempotency
        String fileHash = md5File(filePath);

        if (txtImportLogRepository.existsByFileHash(fileHash)) {
            log.info("Skip BaoCaoNgay file — đã xử lý trước đó: {} (hash: {}...)",
                filePath.getFileName(), fileHash.substring(0, 8));
            return null; // đã xử lý
        }

        // 2. Xử lý đối chiếu + xuất báo cáo
        BaoCaoNgayProcessorService.ProcessResult result =
            baoCaoNgayProcessorService.process(filePath, date);

        // 3. Ghi log idempotency
        TxtImportLog log_ = TxtImportLog.builder()
            .fileName("REPORT:" + filePath.getFileName())
            .fileHash(fileHash)
            .processDate(date)
            .rowsParsed(result.rowsProcessed())
            .rowsOk(result.rowsProcessed() - result.discrepancyCount())
            .rowsError(result.discrepancyCount())
            .errorDetail(result.discrepancyCount() > 0
                ? result.discrepancyCount() + " dòng chênh lệch"
                : null)
            .importedAt(OffsetDateTime.now())
            .build();
        txtImportLogRepository.save(log_);

        // 4. Move file đã xử lý → done/
        moveToSubDir(filePath, "done");

        log.info("✓ BaoCaoNgay done | rows={} | chênh_lệch={} | tongHop={} | banhRaNgay={}",
            result.rowsProcessed(), result.discrepancyCount(),
            result.tongHopFile().getFileName(),
            result.banhRaNgayFile().getFileName());

        return result;
    }

    private void moveToSubDir(Path filePath, String subDir) {
        try {
            Path targetDir = filePath.getParent().resolve(subDir);
            Files.createDirectories(targetDir);
            String ts = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String newName = filePath.getFileName().toString()
                .replace(".xlsx", "_" + ts + ".xlsx");
            Files.move(filePath, targetDir.resolve(newName));
            log.info("Move {} → {}/{}", filePath.getFileName(), subDir, newName);
        } catch (IOException e) {
            log.error("Không thể move file sang {}: {}", subDir, e.getMessage());
        }
    }

    private String md5File(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        MessageDigest md = MessageDigest.getInstance("MD5");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}
