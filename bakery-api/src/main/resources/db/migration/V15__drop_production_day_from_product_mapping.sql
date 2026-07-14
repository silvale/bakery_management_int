-- production_day đã được remove: EX_CODE đã encode đủ thông tin ngày SX theo pattern cố định.
ALTER TABLE product_mapping DROP COLUMN IF EXISTS production_day;
