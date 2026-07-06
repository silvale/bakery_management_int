package com.bakery.common.entity.enums;

public enum PaymentStatus {
    /** Chưa thanh toán */
    UNPAID,
    /** Đã đặt cọc (dùng cho customer order) */
    DEPOSIT,
    /** Thanh toán một phần */
    PARTIAL,
    /** Đã thanh toán đủ */
    PAID
}
