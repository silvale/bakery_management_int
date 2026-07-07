package com.bakery.api.framework.enums;

/**
 * Trạng thái đối chiếu lệnh sản xuất — dùng trong production_order.status.
 * Thay thế ReconcileStatus (đã bỏ trong Architecture V2).
 *
 * PENDING     — Chưa xử lý / chưa có dữ liệu thực tế
 * OK          — Khớp (trong ngưỡng tolerance)
 * OVER        — Sản xuất nhiều hơn yêu cầu
 * UNDER       — Sản xuất ít hơn yêu cầu
 * DISCREPANCY — Có chênh lệch khác (tầng 2 và 3)
 */
public enum ProductionOrderStatus {
    PENDING,
    OK,
    OVER,
    UNDER,
    DISCREPANCY
}
