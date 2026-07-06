package com.bakery.common.entity.enums;

/**
 * Lý do cụ thể của phiếu kho — dùng trong inventory_transaction.transaction_reason (VARCHAR).
 *
 * PURCHASE        — Mua hàng từ nhà cung cấp             (IMPORT)
 * PRODUCTION      — Nhập từ sản xuất (bánh thành phẩm)   (IMPORT)
 * RESTOCK         — Nhập bù hàng trả về / điều chỉnh     (IMPORT | TRANSFER)
 * LOSS            — Hao hụt, thất thoát                   (ADJUSTMENT)
 * STOCKTAKE       — Kiểm đếm tồn kho                      (ADJUSTMENT)
 * SUPPLIER_RETURN — Trả hàng lỗi về nhà cung cấp          (ADJUSTMENT)
 * WRITE_OFF       — Hủy hàng (hết hạn, hư hỏng)           (ADJUSTMENT)
 */
public enum TransactionReason {
    PURCHASE,
    PRODUCTION,
    RESTOCK,
    LOSS,
    STOCKTAKE,
    SUPPLIER_RETURN,
    WRITE_OFF
}
