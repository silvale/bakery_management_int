package com.bakery.batch.config;

import com.bakery.batch.job.DailyBatchJobService;
import com.bakery.common.entity.BatchRun;
import com.bakery.common.entity.enums.BatchRunType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST API để trigger batch job thủ công.
 * Dùng khi chạy file .bat hoặc từ UI admin.
 *
 * POST /batch/jobs/daily?date=2026-04-08
 * POST /batch/jobs/daily (dùng ngày hôm nay)
 */
@Slf4j
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class BatchJobController {

    private final DailyBatchJobService jobService;

    /**
     * Trigger Daily Batch Job.
     *
     * @param date       Ngày xử lý (mặc định = hôm nay)
     * @param triggeredBy  Username trigger (mặc định = MANUAL)
     */
    @PostMapping("/daily")
    public ResponseEntity<Map<String, Object>> runDailyJob(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,

            @RequestParam(defaultValue = "MANUAL")
            String triggeredBy) {

        LocalDate processDate = date != null ? date : LocalDate.now();

        log.info("API trigger Daily Batch | Ngày: {} | By: {}", processDate, triggeredBy);

        try {
            BatchRun batchRun = jobService.run(processDate, triggeredBy, BatchRunType.MANUAL);

            return ResponseEntity.ok(Map.of(
                "batchRunId",  batchRun.getId().toString(),
                "processDate", processDate.toString(),
                "status",      batchRun.getStatus().name(),
                "isRerun",     batchRun.getIsRerun(),
                "startedAt",   batchRun.getStartedAt().toString(),
                "finishedAt",  batchRun.getFinishedAt() != null
                                   ? batchRun.getFinishedAt().toString()
                                   : null
            ));

        } catch (Exception e) {
            log.error("Batch job thất bại: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error",       e.getMessage(),
                "processDate", processDate.toString()
            ));
        }
    }
}
