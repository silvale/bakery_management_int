package com.bakery.common.entity.enums;

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
