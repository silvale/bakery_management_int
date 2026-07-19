-- =====================================================
-- Reset dữ liệu vận hành — GIỮ NGUYÊN master data
--
-- Xoá: kế hoạch SX, phiếu SX, giao nhận, điều chỉnh,
--       tồn kho (stock_lot + stock_movement),
--       báo cáo ngày, hủy bánh, POS sales,
--       kế hoạch ngày (production_plan)
--
-- GIỮ: item, item_group, recipe, recipe_line,
--       supplier, warehouse, product_mapping,
--       product_expiry_config, product_price,
--       production_group, production_threshold_rule,
--       code_value, user_account, user_role
-- =====================================================

BEGIN;

-- 1. POS sales
DELETE FROM pos_daily_sale;

-- 2. Giao nhận + điều chỉnh sản lượng
DELETE FROM production_adjustment;
DELETE FROM delivery_record;

-- 3. Phiếu SX (lines trước, header sau)
DELETE FROM production_request_line;
DELETE FROM production_request;

-- 4. Kế hoạch ngày — phải xoá TRƯỚC daily_report vì có FK generated_from → daily_report.id
DELETE FROM production_plan_line;
DELETE FROM production_plan;

-- 5. Báo cáo ngày (sau khi production_plan đã xoá)
DELETE FROM daily_report_line;
DELETE FROM daily_report;

-- 6. Tồn kho cửa hàng + bếp (stock_lot + movement)
DELETE FROM stock_movement;
DELETE FROM stock_lot;

-- 7. Audit history liên quan (tuỳ chọn — bỏ comment nếu cần xoá luôn)
-- DELETE FROM production_request_line_HIS;
-- DELETE FROM production_request_HIS;
-- DELETE FROM audit_revision_log;

-- 8. Command log vận hành (tuỳ chọn)
-- DELETE FROM command_request WHERE entity_name IN (
--     'ProductionRequest','ProductionRequestLine','DeliveryRecord',
--     'ProductionPlan','DailyReport','PosDailySale'
-- );

COMMIT;

-- Kiểm tra sau khi chạy
SELECT 'daily_report'           AS tbl, COUNT(*) FROM daily_report
UNION ALL SELECT 'daily_report_line',          COUNT(*) FROM daily_report_line
UNION ALL SELECT 'pos_daily_sale',             COUNT(*) FROM pos_daily_sale
UNION ALL SELECT 'production_adjustment',      COUNT(*) FROM production_adjustment
UNION ALL SELECT 'delivery_record',            COUNT(*) FROM delivery_record
UNION ALL SELECT 'production_request_line',    COUNT(*) FROM production_request_line
UNION ALL SELECT 'production_request',         COUNT(*) FROM production_request
UNION ALL SELECT 'production_plan_line',       COUNT(*) FROM production_plan_line
UNION ALL SELECT 'production_plan',            COUNT(*) FROM production_plan
UNION ALL SELECT 'stock_movement',             COUNT(*) FROM stock_movement
UNION ALL SELECT 'stock_lot',                  COUNT(*) FROM stock_lot;
