package com.bakery.common.entity.enums;

/**
 * Trạng thái phiếu kho — dùng trong inventory_transaction.status (VARCHAR).
 *
 * Luồng IMPORT / ADJUSTMENT:  PENDING → ACTIVE | REJECTED  (1 lần duyệt)
 * Luồng TRANSFER:             PENDING → READY → ACTIVE | REJECTED  (2 lần duyệt)
 *
 * PENDING   — Mới tạo, chờ duyệt lần 1 (Cường duyệt)
 * READY     — Đã duyệt lần 1, chờ kho đích xác nhận (chỉ TRANSFER)
 * ACTIVE    — Hoàn tất, đã cập nhật tồn kho
 * REJECTED  — Bị từ chối ở bất kỳ bước nào
 */
public enum TransactionStatus {
    PENDING,
    READY,
    ACTIVE,
    REJECTED
}
