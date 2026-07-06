package com.bakery.api.reconciliation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tổng hợp kết quả 3-way reconciliation cho 1 ngày (hoặc khoảng ngày) + branch.
 */
@Data
@Builder
public class ReconciliationSummaryResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    private UUID      branchId;
    private String    branchName;

    private int totalRows;
    private int okCount;
    private int discrepancyCount;
    private int missingDataCount;

    /** Tổng variance dương (thiếu hàng) */
    private BigDecimal totalPositiveVariance;

    /** Tổng variance âm (thừa / mất mát) */
    private BigDecimal totalNegativeVariance;

    private List<ReconciliationRowResponse> rows;
}
