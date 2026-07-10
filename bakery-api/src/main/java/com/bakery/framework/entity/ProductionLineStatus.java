package com.bakery.framework.entity;

public enum ProductionLineStatus {
    /** Chờ bếp sản xuất */
    PENDING,

    /** Bếp đã hoàn thành và tạo DeliveryRecord */
    COMPLETED
}
