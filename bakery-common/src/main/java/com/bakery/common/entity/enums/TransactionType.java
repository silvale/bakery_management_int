package com.bakery.common.entity.enums;

/**
 * Loại phiếu kho.
 *
 * ── V2 values (inventory_transaction.transaction_type) ───────────────────────
 *   IMPORT     — Nhập hàng (mua từ NCC, nhập từ sản xuất, trả hàng từ shop về kho)
 *   TRANSFER   — Điều chuyển nội bộ giữa các kho (KHO_TONG → KHO_BEP → SHOP)
 *   ADJUSTMENT — Điều chỉnh tồn kho (kiểm đếm, hủy, bù trừ, supplier return)
 *
 * ── Legacy values (InventoryMovement — bảng đã drop, xóa khi Phase 3-5 hoàn tất) ──
 *   EXPORT, RETURN, DISCARD, STOCK_COUNT — giữ lại để cũ code compile được.
 */
public enum TransactionType {

    // ── V2 (Single-Table Ledger) ──────────────────────────────────────────────
    IMPORT,
    TRANSFER,
    ADJUSTMENT,

    // ── V1 Legacy — @Deprecated, xóa sau khi dọn xong Phase 3-5 ─────────────
    /** @deprecated Thay bằng IMPORT hoặc ADJUSTMENT tùy context */
    @Deprecated EXPORT,
    /** @deprecated Thay bằng ADJUSTMENT + transaction_reason = SUPPLIER_RETURN */
    @Deprecated RETURN,
    /** @deprecated Thay bằng ADJUSTMENT + transaction_reason = WRITE_OFF */
    @Deprecated DISCARD,
    /** @deprecated Thay bằng ADJUSTMENT + transaction_reason = STOCKTAKE */
    @Deprecated STOCK_COUNT
}
