-- V38: Bảng cancel_record — lưu danh sách hủy bánh theo EX_CODE
-- Thay thế logic cancel trong daily_report_line, cho phép track per lô (EX_CODE)
-- cancel_type: EXPIRED (hết HSD - do hệ thống tạo) | DAMAGED | OTHER (do NV thêm)

CREATE TABLE cancel_record (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id           UUID         NOT NULL REFERENCES daily_report(id),
    ex_code             VARCHAR(50)  NOT NULL,
    item_id             UUID         REFERENCES item(id),
    production_date     DATE,
    cancel_type         VARCHAR(20)  NOT NULL DEFAULT 'EXPIRED',

    -- Số liệu snapshot tại thời điểm init (để tính còn lại)
    qty_opening         DECIMAL(10,3) NOT NULL DEFAULT 0,  -- tồn đầu ngày từ stock_lot
    qty_received        DECIMAL(10,3) NOT NULL DEFAULT 0,  -- bánh ra thực nhận hôm nay

    -- Số lượng hủy
    qty_cancel_expected DECIMAL(10,3) NOT NULL DEFAULT 0,  -- hệ thống dự kiến (0 nếu hủy vượt)
    qty_cancel_actual   DECIMAL(10,3),                     -- NV nhập thực tế (NULL = chưa xác nhận)

    confirmed           BOOLEAN      NOT NULL DEFAULT FALSE,
    note                VARCHAR(500),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(100),
    updated_by          VARCHAR(100)
);

CREATE INDEX idx_cancel_record_report ON cancel_record(report_id);
CREATE INDEX idx_cancel_record_ex_code ON cancel_record(ex_code);
-- Mỗi EX_CODE chỉ có 1 record EXPIRED per report; DAMAGED/OTHER có thể nhiều
CREATE UNIQUE INDEX uq_cancel_record_expired
    ON cancel_record(report_id, ex_code)
    WHERE cancel_type = 'EXPIRED';

COMMENT ON TABLE cancel_record IS 'Danh sách hủy bánh theo EX_CODE, được tạo khi init report và NV bổ sung hủy vượt';
