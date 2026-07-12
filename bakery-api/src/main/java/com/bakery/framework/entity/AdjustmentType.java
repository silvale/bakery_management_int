package com.bakery.framework.entity;

public enum AdjustmentType {
    /** Bếp lấy nhiều/ít nguyên liệu hơn định mức → cần điều chỉnh tồn kho NL */
    INGREDIENT_VARIANCE,

    /** Hao hụt/lỗi sản xuất → NL đã dùng hết, chỉ ghi nhận output thực tế */
    PRODUCTION_WASTAGE
}
