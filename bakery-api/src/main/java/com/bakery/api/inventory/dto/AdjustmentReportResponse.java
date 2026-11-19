package com.bakery.api.inventory.dto;

import java.util.List;

/** 08/BE-4: {@code requestCount} = số phiếu KHÁC NHAU sau khi lọc (khác tổng {@code requestCount} từng dòng, vốn đếm lặp phiếu nhiều dòng). */
public record AdjustmentReportResponse(List<AdjustmentReportRow> rows, long requestCount) {}
