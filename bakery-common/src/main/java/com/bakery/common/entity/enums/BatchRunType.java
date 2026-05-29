package com.bakery.common.entity.enums;

public enum BatchRunType {
    /** Chạy tự động theo lịch hằng ngày */
    DAILY_AUTO,
    /** Chạy tự động theo lịch hằng tuần (deprecated — dùng DAILY_AUTO) */
    WEEKLY_AUTO,
    /** Chạy thủ công qua file .bat hoặc API */
    MANUAL
}
