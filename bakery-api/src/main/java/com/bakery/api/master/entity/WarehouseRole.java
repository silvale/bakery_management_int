package com.bakery.api.master.entity;

/** Việc một kho được làm; cấu hình bằng cờ trên bảng {@code warehouse} (thiết kế 22 §4.1). */
public enum WarehouseRole {
    RECEIVE_PURCHASE, PRODUCTION_SOURCE, STORE_SEMI, DELIVERY_HOLD, SALES_POINT, EXPORT_EXTERNAL
}
