-- V18: Dọn dẹp các column không còn sử dụng
--
-- 1. production_request.warehouse_id  — concept bỏ từ V8, không map trong ProductionRequest.java
-- 2. production_request.request_date  — superseded bởi production_date (V8), data đã migrate
-- 3. item.selling_price               — V17 đã null hết, canonical price là product_mapping.selling_price

-- Drop index trước (references 2 orphaned columns)
DROP INDEX IF EXISTS idx_production_request_date;

ALTER TABLE production_request
    DROP COLUMN IF EXISTS warehouse_id,
    DROP COLUMN IF EXISTS request_date;

ALTER TABLE item
    DROP COLUMN IF EXISTS selling_price;
