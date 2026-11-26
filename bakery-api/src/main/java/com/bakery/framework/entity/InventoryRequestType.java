package com.bakery.framework.entity;

public enum InventoryRequestType {
    /** Nhập hàng từ nhà cung cấp về kho */
    PURCHASE,

    /** Điều chuyển hàng hóa giữa các kho */
    TRANSFER,

    /** Điều chỉnh kho (tăng/giảm) — cần approval trước khi áp dụng */
    ADJUSTMENT,

    /** 24: Lấy NL từ kho Sản xuất lấy NL sang bảng tạm; thi hành khi người lấy xác nhận. */
    PICK,

    /** 24: Trả NL từ bảng tạm về kho lưu trữ; thi hành ngay. */
    RETURN,

    /** 24: Hao hụt NL ở bảng tạm; thi hành ngay, không cần duyệt. */
    WRITE_OFF,

    /** 25: Xuất cho nơi nhận ngoài (Nhà Hàng): trừ kho xuất khi duyệt, không tạo lô ở nơi nhận. */
    EXPORT
}
