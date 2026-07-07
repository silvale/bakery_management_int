package com.bakery.api.framework.enums;

/**
 * Phân loại chi nhánh / kho.
 *
 * KHO_TONG → nhập NL từ NCC, xuất NL cho Kho Bếp
 * KHO_BEP  → nhận NL, sản xuất bánh, xuất bánh ra Cửa hàng
 * SHOP     → Cửa hàng bán lẻ, nhận bánh, bán cho khách
 */
public enum BranchType {
    KHO_TONG,
    KHO_BEP,
    SHOP
}
