package com.bakery.common.entity.enums;

/**
 * Sub-type của TransactionType — dùng trong InventoryMovement.referenceType
 *
 * ── IMPORT ──────────────────────────────────────────────────────────────────
 * DAILY               — nhập hàng hàng ngày từ nhà cung cấp
 * PURCHASE_ORDER      — nhập theo đơn PO
 * DAMAGE_REPLACEMENT  — NCC bù hàng hỏng
 * TRANSFER_IN         — nhận từ kho khác (goods_transfer COMPLETED)
 *
 * ── EXPORT ──────────────────────────────────────────────────────────────────
 * TO_KITCHEN          — xuất sang KHO_BEP
 * TO_STORE            — xuất accessories sang cửa hàng
 * TRANSFER_OUT        — xuất sang kho khác (goods_transfer COMPLETED)
 *
 * ── RETURN ──────────────────────────────────────────────────────────────────
 * TO_STORAGE          — trả về kho tổng
 * TO_SUPPLIER         — trả về nhà cung cấp
 * INVALID_LIST        — hàng không đúng danh sách
 * INVALID_QUALITY     — hàng không đúng chất lượng
 * INVALID_QUANTITY    — hàng sai khối lượng / số lượng
 *
 * ── DISCARD ─────────────────────────────────────────────────────────────────
 * DAMAGED             — hàng hư hỏng
 * EXPIRED             — hàng hết hạn sử dụng
 *
 * ── ADJUSTMENT ──────────────────────────────────────────────────────────────
 * INCREASE            — tăng tồn kho (điều chỉnh cộng)
 * DECREASE            — giảm tồn kho (điều chỉnh trừ)
 *
 * ── STOCK_COUNT ─────────────────────────────────────────────────────────────
 * END_OF_DAY          — kiểm đếm cuối ngày (từ POS / báo cáo nhân viên)
 * SPOT_CHECK          — kiểm đột xuất
 */
public enum ReferenceType {

    // IMPORT
    DAILY,
    PURCHASE_ORDER,
    DAMAGE_REPLACEMENT,
    TRANSFER_IN,

    // EXPORT
    TO_KITCHEN,
    TO_STORE,
    TRANSFER_OUT,

    // RETURN
    TO_STORAGE,
    TO_SUPPLIER,
    INVALID_LIST,
    INVALID_QUALITY,
    INVALID_QUANTITY,

    // DISCARD
    DAMAGED,
    EXPIRED,

    // ADJUSTMENT
    INCREASE,
    DECREASE,

    // STOCK_COUNT
    END_OF_DAY,
    SPOT_CHECK
}
