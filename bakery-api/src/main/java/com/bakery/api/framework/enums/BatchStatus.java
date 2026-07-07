package com.bakery.api.framework.enums;

public enum BatchStatus {
    RUNNING,
    COMPLETED,
    /** Toàn bộ job thất bại */
    FAILED,
    /** Một số file thất bại, các file khác vẫn xử lý tiếp */
    PARTIAL,
    SUCCESS
}
