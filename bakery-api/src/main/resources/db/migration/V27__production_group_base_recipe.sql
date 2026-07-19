-- V27: Thêm base_recipe_id vào production_group
-- Dùng cho FREE_GROUP (Pana Cotta, v.v.) để tính NL khi plan ở mức group
-- (admin chưa phân bổ từng flavor → dùng công thức base chung × group target qty)

ALTER TABLE production_group
    ADD COLUMN base_recipe_id UUID REFERENCES recipe(id);

COMMENT ON COLUMN production_group.base_recipe_id IS
    'Công thức base chung áp dụng cho toàn nhóm khi plan ở mức group (FREE_GROUP). '
    'NULL = không dùng base recipe, BOM expand từng item riêng.';
