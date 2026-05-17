package com.bakery.common.entity.enums;

public enum ReconcileStatus {
    /** Chưa xử lý */
    PENDING,
    /** Khớp (trong ngưỡng tolerance) */
    OK,
    /** Sản xuất nhiều hơn yêu cầu */
    OVER,
    /** Sản xuất ít hơn yêu cầu */
    UNDER,
    /** Có chênh lệch (dùng cho tầng 2 và 3) */
    DISCREPANCY
}
