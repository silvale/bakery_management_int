package com.bakery.api.framework.enums;

/**
 * Trạng thái của entity trong bảng chính (soft delete).
 * ACTIVE  → đang hoạt động
 * INACTIVE → đã vô hiệu hóa (soft delete)
 */
public enum EntityStatus {
    ACTIVE,
    INACTIVE
}
