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
 * Mặc định: mỗi thứ Hai lúc 06:00 (sau khi cửa hàng đã close và nhân viên đã nộp file).
 *
 * Override qua application.yml:
 *   bakery.batch.schedule.daily-cron: "0 0 6 * * MON"
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class BatchScheduler {

    private final DailyBatchJobService jobService;

    /**
     * Chạy tự động mỗi thứ Hai lúc 06:00 AM.
     * Xử lý dữ liệu của ngày hôm qua (ngày cuối tuần).
     */
    @Scheduled(cron = "${bakery.batch.schedule.daily-cron:0 0 6 * * MON}")
    public void runWeeklyBatch() {
        LocalDate processDate = LocalDate.now().minusDays(1);
        log.info("Scheduler trigger Weekly Batch | Ngày xử lý: {}", processDate);

        try {
            jobService.run(processDate, "SCHEDULER", BatchRunType.WEEKLY_AUTO);
        } catch (Exception e) {
            log.error("Scheduler batch thất bại: {}", e.getMessage(), e);
        }
    }
}
