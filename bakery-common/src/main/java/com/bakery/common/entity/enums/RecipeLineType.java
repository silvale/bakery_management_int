package com.bakery.common.entity.enums;

public enum RecipeLineType {
    /** Phôi bánh — luôn là semi_product */
    PHOI,
    /** Nhân chính — luôn là semi_product */
    NHAN_CHINH,
    /** Nhân phụ gia — có thể là semi_product hoặc ingredient */
    NHAN_PHU,
    /** Trang trí / nguyên liệu trực tiếp — luôn là ingredient */
    TRANG_TRI
}
