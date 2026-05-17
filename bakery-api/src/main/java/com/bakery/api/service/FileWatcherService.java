package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * File Watcher Service.
 *
 * Cron: mỗi 5 phút trong khung 21h-22h.
 * Theo dõi file TXT tồn kho → parse → lưu DailyInventory.
 *
 * Idempotency: MD5 checksum → skip nếu đã import.
 * Dead-letter: file lỗi → move sang thư mục error/.
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class FileWatcherService {

    private final ProductRepository       productRepository;
    private final BranchRepository        branchRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final TxtImportLogRepository  txtImportLogRepository;

    @Value("${bakery.batch.input-dir:./batch-input}")
    private String inputDir;

    private static final String TXT_FILE_NAME = "ton_kho.txt";

    /**
     * Cron: mỗi 5 phút trong khung 21:00 - 22:00
     * Cron expression: 0 0/5 21 * * *  → chạy lúc 21:00, 21:05, 21:10... 21:55
     * Lúc 22:00 không chạy nữa
     */
    @Scheduled(cron = "${bakery.watcher.cron:0 0/5 21 * * *}")
    public void watchAndImport() {
        log.debug("File watcher check: {}/{}", inputDir, TXT_FILE_NAME);

        Path filePath = Paths.get(inputDir, TXT_FILE_NAME);

        if (!Files.exists(filePath)) {
            log.debug("File không tồn tại: {}", filePath);
            return;
        }

        try {
            importTxtFile(filePath, LocalDate.now());
        } catch (Exception e) {
            log.error("File watcher lỗi: {}", e.getMessage(), e);
        }
    }

    /**
     * Import thủ công — dùng cho API trigger hoặc test.
     */
    @Transactional
    public TxtImportResult importTxtFile(Path filePath, LocalDate processDate) throws Exception {
        // 1. Parse file
        TxtInventoryParser.ParseResult parsed = TxtInventoryParser.parse(filePath);

        // 2. Kiểm tra MD5 — skip nếu đã import
        if (txtImportLogRepository.existsByFileHash(parsed.fileHash())) {
            log.info("Skip file TXT — đã import trước đó (hash: {})",
                parsed.fileHash().substring(0, 8) + "...");
            return TxtImportResult.skipped(parsed.fileHash());
        }

        // 3. Load shop branch
        Branch shopBranch = branchRepository.findAll().stream()
            .filter(b -> !b.getIsMain())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Không tìm thấy cửa hàng"));

        // 4. Import từng dòng
        int savedCount = 0;
        StringBuilder errorDetail = new StringBuilder();

        for (TxtInventoryParser.InventoryRow row : parsed.rows()) {
            try {
                Optional<Product> productOpt = productRepository.findByCode(row.spCode());

                if (productOpt.isEmpty()) {
                    String msg = "Không tìm thấy SP: " + row.spCode();
                    log.warn(msg);
                    errorDetail.append(msg).append("\n");
                    continue;
                }

                Product product = productOpt.get();

                // Upsert DailyInventory — cập nhật qty_closing
                Optional<DailyInventory> existingOpt = dailyInventoryRepository
                    .findByBranchIdAndProductIdAndInventoryDate(
                        shopBranch.getId(), product.getId(), processDate);

                if (existingOpt.isPresent()) {
                    DailyInventory existing = existingOpt.get();
                    existing.setQtyClosing(row.qtyClosing());
                    // Tính lại qty_sold_reported = opening + received - closing
                    BigDecimal soldReported = existing.getQtyOpening()
                        .add(existing.getQtyReceived())
                        .subtract(row.qtyClosing());
                    existing.setQtySoldReported(soldReported.max(BigDecimal.ZERO));
                    dailyInventoryRepository.save(existing);
                } else {
                    // Tạo mới với chỉ qty_closing (các field khác = 0)
                    DailyInventory inventory = DailyInventory.builder()
                        .branch(shopBranch)
                        .product(product)
                        .inventoryDate(processDate)
                        .qtyOpening(BigDecimal.ZERO)
                        .qtyReceived(BigDecimal.ZERO)
                        .qtyClosing(row.qtyClosing())
                        .qtySoldReported(BigDecimal.ZERO)
                        .qtyCancelled(BigDecimal.ZERO)
                        .build();
                    dailyInventoryRepository.save(inventory);
                }

                savedCount++;

            } catch (Exception e) {
                String msg = "Lỗi dòng " + row.lineNum() + " (" + row.spCode() + "): " + e.getMessage();
                log.error(msg, e);
                errorDetail.append(msg).append("\n");
            }
        }

        // 5. Lưu TxtImportLog
        TxtImportLog importLog = TxtImportLog.builder()
            .fileName(filePath.getFileName().toString())
            .fileHash(parsed.fileHash())
            .processDate(processDate)
            .rowsParsed(parsed.totalLines())
            .rowsOk(savedCount)
            .rowsError(parsed.errorCount() + (savedCount - parsed.okCount()))
            .errorDetail(errorDetail.length() > 0 ? errorDetail.toString() : null)
            .importedAt(OffsetDateTime.now())
            .build();
        txtImportLogRepository.save(importLog);

        // 6. Move file lỗi sang error/ nếu có lỗi parse
        if (parsed.hasErrors()) {
            moveToErrorDir(filePath, parsed.errors());
        }

        log.info("✓ Import TXT xong | OK: {} | Lỗi: {} | Hash: {}",
            savedCount, parsed.errorCount(), parsed.fileHash().substring(0, 8));

        return new TxtImportResult(
            parsed.fileHash(), false, savedCount,
            parsed.errorCount(), parsed.errors()
        );
    }

    // ── Helpers ───────────────────────────────────────────────

    private void moveToErrorDir(Path filePath, List<String> errors) {
        try {
            Path errorDir = filePath.getParent().resolve("error");
            Files.createDirectories(errorDir);

            String timestamp = OffsetDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String errorFileName = filePath.getFileName().toString()
                .replace(".txt", "_" + timestamp + "_error.txt");

            Path errorFilePath = errorDir.resolve(errorFileName);

            // Tạo file error kèm danh sách lỗi
            StringBuilder content = new StringBuilder();
            content.append("# Lỗi import ngày ")
                .append(LocalDate.now())
                .append("\n\n");
            errors.forEach(e -> content.append("ERROR: ").append(e).append("\n"));

            // Copy nội dung gốc vào cuối
            content.append("\n# Nội dung gốc:\n");
            Files.readAllLines(filePath).forEach(l -> content.append(l).append("\n"));

            Files.writeString(errorFilePath, content.toString());
            log.warn("File lỗi → {}", errorFilePath);

        } catch (Exception e) {
            log.error("Không thể move file lỗi: {}", e.getMessage());
        }
    }

    // ── Result record ─────────────────────────────────────────

    public record TxtImportResult(
        String       fileHash,
        boolean      skipped,
        int          savedCount,
        int          errorCount,
        List<String> errors
    ) {
        static TxtImportResult skipped(String hash) {
            return new TxtImportResult(hash, true, 0, 0, List.of());
        }
    }
}
