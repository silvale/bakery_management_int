package com.bakery.framework.entity;

public enum InventoryRequestType {
    /** Nhập hàng từ nhà cung cấp về kho */
    PURCHASE,

    /** Điều chuyển hàng hóa giữa các kho */
    TRANSFER,

    /** Điều chỉnh kho (tăng/giảm) — cần approval trước khi áp dụng */
    ADJUSTMENT
}
