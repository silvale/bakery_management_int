-- ============================================================
-- DROP ALL — Bakery Management System (Architecture V2)
-- Chạy script này khi cần reset DB hoàn toàn.
-- Thứ tự: bảng con trước, bảng cha sau.
-- ============================================================

DROP TABLE IF EXISTS
    -- Auth
    refresh_token,
    role_permission,
    user_profile,
    user_role,
    screen_registry,

    -- Activity / Admin
    activity_log,
    entity_revision_log,
    command_request,

    -- Batch / Import logs
    txt_import_log,
    file_import_log,
    batch_run,

    -- Reconciliation sources
    daily_shop_report,
    pos_sales_data,

    -- Inventory Ledger (core)
    inventory_transaction_payment,
    inventory_transaction_line,
    inventory_transaction,
    inventory,

    -- Customer orders
    customer_order_line_addon,
    customer_order_payment,
    customer_order_line,
    customer_order,

    -- Production operations
    production_request,
    production_plan_line,
    production_plan,
    production_lot,
    production_order_line,
    production_order,

    -- Production config
    production_group_member,
    production_group,
    batch_formula_config,
    production_template,

    -- Recipe / BOM
    recipe_line,
    recipe,
    recipe_line_semi,

    -- Product mapping
    product_mapping,

    -- Config / Pricing
    unit_conversion,
    ingredient_price,
    product_prefix,
    product_expiry_config,
    product_price,

    -- Master data
    semi_product,
    supplier,
    ingredient,
    product,
    branch
CASCADE;

-- Drop views
DROP VIEW IF EXISTS v_reconciliation CASCADE;

-- Drop enum types
DROP TYPE IF EXISTS
    production_order_status,
    customer_order_status,
    request_status,
    request_type,
    payment_status,
    lot_status,
    lot_cost_status,
    recipe_line_type,
    semi_product_type,
    base_unit,
    product_type,
    batch_run_type,
    batch_status,
    file_type,
    command_action,
    command_status,
    entity_status,
    branch_type,
    -- Legacy enums (may exist on older installations)
    reconcile_status,
    stock_lot_status,
    transfer_status,
    transfer_confirm_status,
    plan_status,
    transaction_type,
    reference_type,
    write_off_reason,
    supplier_return_status,
    movement_type,
    movement_reason
CASCADE;
