package com.bakery.common.entity.enums;

/**
 * @deprecated Thay bằng {@link ProductionOrderStatus} (Architecture V2).
 *             ProductionOrder.status đã được cập nhật. Xóa file này sau khi
 *             dọn xong Phase 3-5.
 */
@Deprecated
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
