package com.bakery.api.framework.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BatchRunRequest {

    /**
     * Ngày cần xử lý. Mặc định = hôm nay nếu không truyền.
     * Format: yyyy-MM-dd
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate processDate;

    /** Username trigger. Mặc định = MANUAL */
    private String triggeredBy = "MANUAL";
}
