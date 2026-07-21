-- =====================================================
-- Reset dữ liệu vận hành — GIỮ NGUYÊN master data
-- =====================================================
-- BƯỚC 1: Clear bất kỳ transaction cũ nào đang bị abort
ROLLBACK;

-- BƯỚC 2: Bắt đầu transaction mới
BEGIN;

DELETE FROM pos_daily_sale;
DELETE FROM production_adjustment;
DELETE FROM delivery_record;
DELETE FROM production_request_line;
DELETE FROM production_request;
DELETE FROM production_plan_line;
DELETE FROM production_plan_group;
DELETE FROM production_plan;
DELETE FROM daily_report_line;
DELETE FROM daily_report;
DELETE FROM inventory_request_line;
DELETE FROM inventory_request;
DELETE FROM stock_movement;
DELETE FROM stock_lot;

COMMIT;

-- Kiểm tra: tất cả phải = 0
SELECT 'pos_daily_sale'         AS tbl, COUNT(*) AS cnt FROM pos_daily_sale
UNION ALL SELECT 'production_adjustment',   COUNT(*) FROM production_adjustment
UNION ALL SELECT 'delivery_record',         COUNT(*) FROM delivery_record
UNION ALL SELECT 'production_request_line', COUNT(*) FROM production_request_line
UNION ALL SELECT 'production_request',      COUNT(*) FROM production_request
UNION ALL SELECT 'production_plan_line',    COUNT(*) FROM production_plan_line
UNION ALL SELECT 'production_plan_group',   COUNT(*) FROM production_plan_group
UNION ALL SELECT 'production_plan',         COUNT(*) FROM production_plan
UNION ALL SELECT 'daily_report_line',       COUNT(*) FROM daily_report_line
UNION ALL SELECT 'daily_report',            COUNT(*) FROM daily_report
UNION ALL SELECT 'inventory_request_line',  COUNT(*) FROM inventory_request_line
UNION ALL SELECT 'inventory_request',       COUNT(*) FROM inventory_request
UNION ALL SELECT 'stock_movement',          COUNT(*) FROM stock_movement
UNION ALL SELECT 'stock_lot',               COUNT(*) FROM stock_lot;
