package com.bakery.framework.entity;

public enum DailyReportStatus {
    /** Đang trong ngày, có thể cập nhật */
    DRAFT,

    /** Admin đã chốt — không còn chỉnh sửa được */
    FINALIZED
}
