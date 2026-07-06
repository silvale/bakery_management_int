package com.bakery.api.controller;

import com.bakery.api.dto.BatchRunRequest;
import com.bakery.api.dto.ReconcileResultResponse;
import com.bakery.api.service.BatchApiService;
import com.bakery.api.service.PosFileProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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

    private final BatchApiService        batchApiService;
    private final PosFileProcessorService posFileProcessorService;

    /**
     * POST /batch/run
     */
    @PostMapping("/run")
    @Operation(summary = "Chạy batch: đọc file và reconcile")
    public ResponseEntity<ReconcileResultResponse> run(
            @RequestBody(required = false) BatchRunRequest request) {

        LocalDate processDate = (request != null && request.getProcessDate() != null)
            ? request.getProcessDate() : LocalDate.now();
        String triggeredBy = (request != null && request.getTriggeredBy() != null)
            ? request.getTriggeredBy() : "MANUAL";

        log.info("POST /batch/run | date={} by={}", processDate, triggeredBy);
        return ResponseEntity.ok(batchApiService.runAndReconcile(processDate, triggeredBy));
    }

    /**
     * GET /batch/result?date=2026-04-08
     */
    @GetMapping("/result")
    @Operation(summary = "Xem kết quả reconcile theo ngày")
    public ResponseEntity<ReconcileResultResponse> getResult(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("GET /batch/result | date={}", date);
        return ResponseEntity.ok(batchApiService.getResult(date));
    }

    /**
     * POST /batch/pos/upload?date=2026-05-29
     * Upload file POS thủ công → xử lý ngay.
     */
    @PostMapping("/pos/upload")
    @Operation(summary = "Upload file POS thủ công",
               description = "Upload file POS Excel → decode EX_CODE → cập nhật qtySold")
    public ResponseEntity<Map<String, Object>> uploadPos(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate processDate = date != null ? date : LocalDate.now();
        log.info("POST /batch/pos/upload | date={} | file={}", processDate, file.getOriginalFilename());

        try {
            Path tmp = Files.createTempFile("pos_", ".xlsx");
            file.transferTo(tmp.toFile());

            PosFileProcessorService.ProcessResult result =
                posFileProcessorService.process(tmp, processDate);

            Files.deleteIfExists(tmp);

            return ResponseEntity.ok(Map.of(
                "status",           "OK",
                "rowsParsed",       result.rowsParsed(),
                "distinctProducts", result.distinctProducts(),
                "lotsUpdated",      result.lotsUpdated(),
                "warnings",         result.warnings()
            ));

        } catch (Exception e) {
            log.error("Upload POS thất bại: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    /**
     * POST /batch/report/upload — V12: deprecated, dùng UI thay thế
     */
    @PostMapping("/report/upload")
    @Operation(summary = "[Deprecated V12] Upload BaoCaoNgay — dùng UI thay thế")
    public ResponseEntity<Map<String, Object>> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: BaoCaoNgay không còn upload file. Dùng UI ProductionPlan/StockTransfer."
        ));
    }

    /**
     * POST /batch/bepxuat/upload — V12: deprecated, dùng ProductionRequest UI
     */
    @PostMapping("/bepxuat/upload")
    @Operation(summary = "[Deprecated V12] Upload BanhRaNgay bếp — dùng UI thay thế")
    public ResponseEntity<Map<String, Object>> uploadBepXuat(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: Bếp khai báo sản xuất qua ProductionRequest UI."
        ));
    }

    /**
     * POST /batch/opening-stock/upload — V12: deprecated
     */
    @PostMapping("/opening-stock/upload")
    @Operation(summary = "[Deprecated V12] Import tồn kho ban đầu — dùng UI thay thế")
    public ResponseEntity<Map<String, Object>> uploadOpeningStock(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: Nhập tồn kho ban đầu qua InventoryAdjustment UI."
        ));
    }

    /**
     * GET /batch/export — V12: deprecated
     */
    @GetMapping("/export")
    @Operation(summary = "[Deprecated V12] Export báo cáo Excel — dùng UI thay thế")
    public ResponseEntity<Map<String, Object>> exportExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.status(410).body(Map.of(
            "status", "DEPRECATED",
            "message", "V12: Báo cáo xem trực tiếp trên UI, không còn xuất Excel."
        ));
    }
}
