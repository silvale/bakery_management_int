package com.bakery.api.controller;

import com.bakery.api.dto.BatchRunRequest;
import com.bakery.api.dto.ReconcileResultResponse;
import com.bakery.api.service.BatchApiService;
import com.bakery.api.service.BaoCaoNgayProcessorService;
import com.bakery.api.service.PosFileProcessorService;
import com.bakery.api.service.PosFileWatcherService;
import com.bakery.api.service.ReportExcelService;
import com.bakery.api.service.ReportFileWatcherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
@Tag(name = "Batch", description = "Đọc file Excel và đối chiếu dữ liệu")
public class BatchController {

    private final BatchApiService         batchApiService;
    private final ReportExcelService      reportExcelService;
    private final PosFileWatcherService   posFileWatcherService;
    private final ReportFileWatcherService reportFileWatcherService;

    /**
     * POST /batch/run
     * Đọc 4 file Excel → import DB → reconcile → trả kết quả ngay.
     *
     * Body (optional):
     * {
     *   "processDate": "2026-04-08",
     *   "triggeredBy": "admin"
     * }
     */
    @PostMapping("/run")
    @Operation(summary = "Chạy batch: đọc 4 file và reconcile",
               description = "Đọc BanhRaNgay, XuatRa, BaoCaoNgay, BigProductBySaleByCat → lưu DB → trả kết quả đối chiếu")
    public ResponseEntity<ReconcileResultResponse> run(
            @RequestBody(required = false) BatchRunRequest request) {

        LocalDate processDate = (request != null && request.getProcessDate() != null)
            ? request.getProcessDate()
            : LocalDate.now();

        String triggeredBy = (request != null && request.getTriggeredBy() != null)
            ? request.getTriggeredBy()
            : "MANUAL";

        log.info("POST /batch/run | date={} by={}", processDate, triggeredBy);

        ReconcileResultResponse result = batchApiService.runAndReconcile(processDate, triggeredBy);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /batch/result?date=2026-04-08
     * Lấy kết quả reconcile đã có trong DB (không chạy lại).
     */
    @GetMapping("/result")
    @Operation(summary = "Xem kết quả reconcile",
               description = "Lấy kết quả đối chiếu đã lưu trong DB theo ngày")
    public ResponseEntity<ReconcileResultResponse> getResult(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("GET /batch/result | date={}", date);
        ReconcileResultResponse result = batchApiService.getResult(date);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /batch/pos/upload?date=2026-05-29
     * Upload file POS thủ công → xử lý ngay.
     */
    @PostMapping("/pos/upload")
    @Operation(summary = "Upload file POS thủ công",
               description = "Upload file POS Excel → decode EX_CODE → cập nhật qtySold → xuất HuyBanh")
    public ResponseEntity<Map<String, Object>> uploadPos(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate processDate = date != null ? date : LocalDate.now();
        log.info("POST /batch/pos/upload | date={} | file={}", processDate, file.getOriginalFilename());

        try {
            Path tmp = Files.createTempFile("pos_", ".xlsx");
            file.transferTo(tmp.toFile());

            PosFileProcessorService.ProcessResult result =
                posFileWatcherService.processManual(tmp, processDate);

            Files.deleteIfExists(tmp);

            if (result == null) {
                return ResponseEntity.ok(Map.of(
                    "status", "SKIPPED",
                    "message", "File đã được xử lý trước đó (MD5 trùng)"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "status", "OK",
                "rowsParsed", result.rowsParsed(),
                "lotsUpdated", result.lotsUpdated(),
                "warnings", result.warnings(),
                "huyBanhFile", result.huyBanhFile().getFileName().toString()
            ));

        } catch (Exception e) {
            log.error("Upload POS thất bại: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * POST /batch/report/upload?date=2026-05-29
     * Upload file BaoCaoNgay thủ công → xử lý ngay.
     */
    @PostMapping("/report/upload")
    @Operation(summary = "Upload file BaoCaoNgay thủ công",
               description = "Upload file BaoCaoNgay Excel → đối chiếu → xuất TongHop + BanhRaNgay")
    public ResponseEntity<Map<String, Object>> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate processDate = date != null ? date : LocalDate.now();
        log.info("POST /batch/report/upload | date={} | file={}", processDate, file.getOriginalFilename());

        try {
            Path tmp = Files.createTempFile("report_", ".xlsx");
            file.transferTo(tmp.toFile());

            BaoCaoNgayProcessorService.ProcessResult result =
                reportFileWatcherService.processManual(tmp, processDate);

            Files.deleteIfExists(tmp);

            if (result == null) {
                return ResponseEntity.ok(Map.of(
                    "status", "SKIPPED",
                    "message", "File đã được xử lý trước đó (MD5 trùng)"
                ));
            }

            return ResponseEntity.ok(Map.of(
                "status", "OK",
                "rowsProcessed", result.rowsProcessed(),
                "discrepancyCount", result.discrepancyCount(),
                "tongHopFile", result.tongHopFile().getFileName().toString(),
                "banhRaNgayFile", result.banhRaNgayFile().getFileName().toString()
            ));

        } catch (Exception e) {
            log.error("Upload BaoCaoNgay thất bại: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * GET /batch/export?date=2026-04-08
     * Export báo cáo ngày ra file Excel.
     * Highlight đỏ các dòng có lỗi.
     */
    @GetMapping("/export")
    @Operation(summary = "Export báo cáo ngày ra Excel",
               description = "Xuất file Excel với highlight đỏ các dòng có chênh lệch")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("GET /batch/export | date={}", date);

        try {
            byte[] excelBytes = reportExcelService.exportDailyReport(date);

            String filename = "BaoCaoNgay_" +
                date.format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy")) + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(excelBytes.length);

            return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);

        } catch (Exception e) {
            log.error("Export Excel thất bại: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}