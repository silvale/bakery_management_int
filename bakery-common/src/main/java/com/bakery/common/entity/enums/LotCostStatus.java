package com.bakery.common.entity.enums;

public enum LotCostStatus {
    /** Cost đã chốt chính xác theo FIFO */
    CONFIRMED,
    /** Tồn kho âm — cost tạm tính, chờ backdate nhập kho */
    PENDING
}
