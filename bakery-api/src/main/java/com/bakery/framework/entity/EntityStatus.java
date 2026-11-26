package com.bakery.framework.entity;

public enum EntityStatus {
    ACTIVE,
    INACTIVE;

    /** 26: nhãn tiếng Việt ("Đã xóa" = xóa mềm). */
    public String label() { return this == ACTIVE ? "Đang dùng" : "Đã xóa"; }
}
