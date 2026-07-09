-- =============================================================
-- truncate_all_except_code_value.sql
-- Xóa toàn bộ data, giữ lại code_value
-- Chạy: psql -U <user> -d <db> -f truncate_all_except_code_value.sql
-- =============================================================

TRUNCATE TABLE
    -- Giao dịch kho
    stock_movement,
    stock_lot,
    stock_adjustment,
    inventory_request_line,
    inventory_request,

    -- Sản xuất
    production_request,

    -- Giá
    ingredient_price,
    product_price,

    -- Công thức
    recipe_line,
    recipe,

    -- Kế hoạch sản xuất
    product_plan_template_line,
    product_plan_template,

    -- Hạn sử dụng
    product_expiry_config,

    -- Master data
    product_mapping,
    item,
    supplier,
    warehouse,

    -- Audit / command
    command_request,

    -- Auth
    refresh_token,
    user_account,
    user_role

CASCADE;
