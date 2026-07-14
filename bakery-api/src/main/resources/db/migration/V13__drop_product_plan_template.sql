-- =============================================================
-- V13__drop_product_plan_template.sql
-- Xóa product_plan_template + product_plan_template_line.
-- Chức năng "số lượng cố định mỗi ngày" đã được thay thế
-- hoàn toàn bởi production_threshold_rule + production_group.
-- =============================================================

DROP TABLE IF EXISTS product_plan_template_line;
DROP TABLE IF EXISTS product_plan_template;
