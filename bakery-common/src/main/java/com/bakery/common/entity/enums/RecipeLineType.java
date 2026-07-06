package com.bakery.common.entity.enums;

public enum RecipeLineType {
    /** Phôi bánh — luôn là semi_product */
    PHOI,
    /** Nhân chính — luôn là semi_product */
    NHAN_CHINH,
    /** Nhân phụ gia — có thể là semi_product hoặc ingredient */
    NHAN_PHU,
    /** Trang trí — có thể là semi_product (SỐT MẶN, DA BEO...) hoặc ingredient trực tiếp */
    TRANG_TRI,
    /** Bao bì đóng gói — luôn là ingredient (unit=PCS): hộp, túi, dĩa nĩa, nến... */
    BAOBI
}
