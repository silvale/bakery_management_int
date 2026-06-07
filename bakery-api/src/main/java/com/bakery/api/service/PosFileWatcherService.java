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
 * POS File Watcher Service.
 *
 * Cron: mỗi 5 phút trong khung 21:30 - 22:25 (để đảm bảo không bỏ sót).
 * Theo dõi thư mục /input/pos/ tìm file .xlsx → xử lý POS → xuất HuyBanh.
 *
 * Idempotency: MD5 checksum → skip nếu đã xử lý.
 * Dead-letter:  file lỗi → move sang /input/pos/error/
 * Done:         file thành công → move sang /input/pos/done/
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class PosFileWatcherService {

    private final PosFileProcessorService  posFileProcessorService;
    private final TxtImportLogRepository   txtImportLogRepository;

    @Value("${bakery.batch.input-dir:./batch-input}")
    private String inputDir;

    private static final String POS_SUBDIR = "pos";

    /**
     * Cron: mỗi 5 phút, khung 21:30 đến 22:25
     * → 21:30, 21:35, 21:40, 21:45, 21:50, 21:55, 22:00, 22:05, 22:10, 22:15, 22:20, 22:25
     */
    @Scheduled(cron = "${bakery.watcher.pos.cron:0 30/5 21 * * *}")
    public void watchPosDirectory() {
        Path watchDir = Paths.get(inputDir, POS_SUBDIR);
        if (!Files.exists(watchDir)) {
            log.debug("POS watch dir chưa tồn tại: {}", watchDir);
            return;
        }

        try (Stream<Path> files = Files.list(watchDir)) {
            List<Path> xlsxFiles = files
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                .filter(p -> Files.isRegularFile(p))
                .sorted()
                .toList();

            if (xlsxFiles.isEmpty()) {
                log.debug("Không có file POS mới trong {}", watchDir);
                return;
            }

            for (Path file : xlsxFiles) {
                processPosFile(file);
            }

        } catch (IOException e) {
            log.error("POS watcher lỗi khi liệt kê thư mục: {}", e.getMessage(), e);
        }
    }

    /**
     * Xử lý thủ công — dùng cho API trigger hoặc test.
     */
    public PosFileProcessorService.ProcessResult processManual(Path filePath, LocalDate date) throws Exception {
        return processFileInternal(filePath, date);
    }

    // ── Internal ──────────────────────────────────────────────

    private void processPosFile(Path filePath) {
        log.info("POS watcher: phát hiện file {}", filePath.getFileName());
        try {
            processFileInternal(filePath, LocalDate.now());
        } catch (Exception e) {
            log.error("POS xử lý thất bại [{}]: {}", filePath.getFileName(), e.getMessage(), e);
            moveToSubDir(filePath, "error");
        }
    }

    private PosFileProcessorService.ProcessResult processFileInternal(Path filePath, LocalDate date) throws Exception {
        // 1. Tính MD5 để kiểm tra idempotency
        String fileHash = md5File(filePath);

        if (txtImportLogRepository.existsByFileHash(fileHash)) {
            log.info("Skip POS file — đã xử lý trước đó: {} (hash: {}...)",
                filePath.getFileName(), fileHash.substring(0, 8));
            return null; // đã xử lý
        }

        // 2. Xử lý
        PosFileProcessorService.ProcessResult result = posFileProcessorService.process(filePath, date);

        // 3. Ghi log idempotency
        TxtImportLog log_ = TxtImportLog.builder()
            .fileName("POS:" + filePath.getFileName())
            .fileHash(fileHash)
            .processDate(date)
            .rowsParsed(result.rowsParsed())
            .rowsOk(result.lotsUpdated())
            .rowsError(result.warnings().size())
            .errorDetail(result.warnings().isEmpty() ? null : String.join("\n", result.warnings()))
            .importedAt(OffsetDateTime.now())
            .build();
        // TEMP COMMENT FOR TESTING
//        txtImportLogRepository.save(log_);

        // 4. Move file đã xử lý → done/
        moveToSubDir(filePath, "done");

        log.info("✓ POS done | rows={} | lotsUpdated={} | warnings={} | file={}",
            result.rowsParsed(), result.lotsUpdated(), result.warnings().size(), filePath.getFileName());

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
