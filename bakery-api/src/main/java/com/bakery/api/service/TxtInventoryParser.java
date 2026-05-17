package com.bakery.api.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Parse file TXT tồn kho cuối ngày.
 *
 * Format mỗi dòng:
 *   SP022575 | Bánh mì dài | 5
 *   SP001785 | Dứa dừa     | 3
 *
 * Rules:
 *   - Dòng trống → bỏ qua
 *   - Dòng bắt đầu bằng # → comment, bỏ qua
 *   - Mã SP phải bắt đầu bằng "SP"
 *   - Số lượng phải >= 0
 */
@Slf4j
public class TxtInventoryParser {

    private TxtInventoryParser() {}

    public static ParseResult parse(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        String fileHash = md5(String.join("\n", lines));

        List<InventoryRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int lineNum = 0;

        for (String line : lines) {
            lineNum++;
            line = line.trim();

            // Bỏ qua dòng trống và comment
            if (line.isBlank() || line.startsWith("#")) continue;

            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                errors.add("Dòng " + lineNum + ": format sai (cần SP|Tên|SL) → " + line);
                continue;
            }

            String spCode = parts[0].trim().toUpperCase();
            String name   = parts[1].trim();
            String qtyStr = parts[2].trim();

            // Validate mã SP
            if (!spCode.startsWith("SP")) {
                errors.add("Dòng " + lineNum + ": mã SP không hợp lệ → " + spCode);
                continue;
            }

            // Validate số lượng
            BigDecimal qty;
            try {
                qty = new BigDecimal(qtyStr);
                if (qty.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add("Dòng " + lineNum + ": số lượng âm → " + qtyStr);
                    continue;
                }
            } catch (NumberFormatException e) {
                errors.add("Dòng " + lineNum + ": số lượng không hợp lệ → " + qtyStr);
                continue;
            }

            rows.add(new InventoryRow(spCode, name, qty, lineNum));
        }

        log.info("TXT parse: {} dòng hợp lệ, {} lỗi | hash: {}",
            rows.size(), errors.size(), fileHash.substring(0, 8) + "...");

        return new ParseResult(fileHash, rows, errors,
            lineNum, rows.size(), errors.size());
    }

    // MD5 của nội dung file — dùng để tránh re-import
    public static String md5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    // ── Records ───────────────────────────────────────────────

    public record InventoryRow(
        String     spCode,
        String     name,
        BigDecimal qtyClosing,
        int        lineNum
    ) {}

    public record ParseResult(
        String             fileHash,
        List<InventoryRow> rows,
        List<String>       errors,
        int totalLines,
        int okCount,
        int errorCount
    ) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }
}
