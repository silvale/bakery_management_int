package com.bakery.batch.config;

import com.bakery.batch.job.DailyBatchJobService;
import com.bakery.common.entity.enums.BatchRunType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduler tự động chạy batch.
 * Mặc định: mỗi ngày lúc 06:00 (sau khi cửa hàng đã close và nhân viên đã nộp file).
 *
 * Override qua application.yml:
 *   bakery.batch.schedule.daily-cron: "0 0 6 * * *"
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class BatchScheduler {

    private final DailyBatchJobService jobService;

    /**
     * Chạy tự động mỗi ngày lúc 06:00 AM.
     * Xử lý dữ liệu của ngày hôm qua.
     */
    @Scheduled(cron = "${bakery.batch.schedule.daily-cron:0 0 6 * * *}")
    public void runDailyBatch() {
        LocalDate processDate = LocalDate.now().minusDays(1);
        log.info("Scheduler trigger Daily Batch | Ngày xử lý: {}", processDate);

        try {
            jobService.run(processDate, "SCHEDULER", BatchRunType.DAILY_AUTO);
        } catch (Exception e) {
            log.error("Scheduler batch thất bại: {}", e.getMessage(), e);
        }
    }
}
