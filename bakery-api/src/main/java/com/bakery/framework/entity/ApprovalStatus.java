package com.bakery.framework.entity;

public enum ApprovalStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED;

    /** 26: nhãn tiếng Việt để ghép vào câu lỗi cho người dùng (không in tên enum). */
    public String label() {
        return switch (this) {
            case DRAFT -> "Nháp";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Bị từ chối";
        };
    }
}
