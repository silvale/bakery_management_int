package com.bakery.framework.entity;

public enum AdjustmentSource {
    /** Tạo tự động khi bếp bấm Complete với qtyProduced ≠ plannedQty */
    KITCHEN_COMPLETE,

    /** Admin tạo thủ công để sửa sau khi bếp đã submit */
    ADMIN_CORRECTION
}
