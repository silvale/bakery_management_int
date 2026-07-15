-- =============================================================
-- reset_operational_data.sql
--
-- Xóa toàn bộ dữ liệu vận hành (request, report, kho, cửa hàng...)
-- GIỮ LẠI: sản phẩm, công thức, template, cấu hình, tài khoản
--
-- Chạy: psql -U <user> -d <db> -f reset_operational_data.sql
-- =============================================================

BEGIN;

-- ── Kế hoạch sản xuất ────────────────────────────────────────
DELETE FROM production_plan_line;
DELETE FROM production_plan;

-- ── Báo cáo ngày ─────────────────────────────────────────────
DELETE FROM daily_report_line;
DELETE FROM daily_report;

-- ── POS & điều chỉnh ─────────────────────────────────────────
DELETE FROM pos_daily_sale;
DELETE FROM production_adjustment;

-- ── Giao hàng & yêu cầu sản xuất ────────────────────────────
DELETE FROM delivery_record;
DELETE FROM production_request_line;
DELETE FROM production_request;

-- ── Kho: tồn kho, lô hàng, phiếu nhập/xuất ──────────────────
DELETE FROM stock_movement;
DELETE FROM stock_lot;
DELETE FROM inventory_request_line;
DELETE FROM inventory_request;

-- ── Session auth ─────────────────────────────────────────────
DELETE FROM refresh_token;

-- ── Audit log ────────────────────────────────────────────────
DELETE FROM command_request;

COMMIT;

-- Kiểm tra nhanh
SELECT
    (SELECT COUNT(*) FROM production_plan)        AS production_plan,
    (SELECT COUNT(*) FROM daily_report)           AS daily_report,
    (SELECT COUNT(*) FROM inventory_request)      AS inventory_request,
    (SELECT COUNT(*) FROM stock_lot)              AS stock_lot,
    (SELECT COUNT(*) FROM production_request)     AS production_request,
    -- Những bảng này phải còn nguyên:
    (SELECT COUNT(*) FROM item)                   AS item_kept,
    (SELECT COUNT(*) FROM recipe)                 AS recipe_kept,
    (SELECT COUNT(*) FROM warehouse)              AS warehouse_kept,
    (SELECT COUNT(*) FROM user_account)           AS user_account_kept;
