package com.bakery.batch.util;

import com.bakery.common.entity.BatchRun;
import com.bakery.common.entity.FileImportLog;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Giữ context của 1 lần chạy batch (BatchRun + process date).
 * Inject vào các Step để dùng chung.
 *
 * Dùng Spring JobScope hoặc truyền qua JobExecutionContext.
 * Class này dùng để wrap các giá trị hay dùng.
 */
@Getter
@Setter
@Component
public class BatchContextHolder {

    private UUID batchRunId;
    private LocalDate processDate;
    private String triggeredBy;

    public void reset() {
        this.batchRunId   = null;
        this.processDate  = null;
        this.triggeredBy  = null;
    }
}
