package com.bakery.api.controller;

import com.bakery.api.dto.BatchRunRequest;
import com.bakery.api.dto.ReconcileResultResponse;
import com.bakery.api.service.BatchApiService;
import com.bakery.api.service.ReportExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
@Tag(name = "Batch", description = "Đọc file Excel và đối chiếu dữ liệu")
public class BatchController {

    private final BatchApiService batchApiService;
    private final ReportExcelService reportExcelService;

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