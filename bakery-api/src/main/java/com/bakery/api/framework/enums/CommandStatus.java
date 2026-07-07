package com.bakery.api.framework.enums;

/**
 * Trạng thái của command_request.
 * PENDING  → chờ admin duyệt
 * APPROVED → đã duyệt, đã execute vào bảng chính
 * REJECTED → đã từ chối
 */
public enum CommandStatus {
    PENDING,
    APPROVED,
    REJECTED
}
