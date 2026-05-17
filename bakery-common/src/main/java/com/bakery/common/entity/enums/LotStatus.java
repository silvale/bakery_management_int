package com.bakery.common.entity.enums;

public enum LotStatus {
    /** Đang bán, còn hàng */
    ACTIVE,
    /** Đã hủy toàn bộ */
    CANCELLED,
    /** Quá hạn sử dụng */
    EXPIRED,
    /** Hủy một phần, còn lại */
    PARTIAL
}
