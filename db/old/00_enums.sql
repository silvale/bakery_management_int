-- =============================================================
-- 00_enums.sql
-- Final consolidated enum types (V1 → V12)
-- Generated from: V1__create_schema.sql, V6__cost_recipes.sql,
--                 V7__admin_framework.sql, V11__warehouse_split.sql,
--                 V12__redesign.sql
-- =============================================================

-- ---------------------------------------------------------------------------
-- EXTENSIONS
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------------
-- ENUMS (all merged to final state)
-- ---------------------------------------------------------------------------

-- product_type (V1 base + V12 adds ACCESSORY)
DO $$ BEGIN
    CREATE TYPE product_type AS ENUM ('STANDARD', 'SHEET_CAKE', 'ACCESSORY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- base_unit (V1 base + V6 adds PCS)
DO $$ BEGIN
    CREATE TYPE base_unit AS ENUM ('GRAM', 'ML', 'PCS');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- semi_product_type (V1 base + V6 adds TRANG_TRI)
DO $$ BEGIN
    CREATE TYPE semi_product_type AS ENUM ('PHOI', 'NHAN', 'TRANG_TRI');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- recipe_line_type (V1 base + V6 adds BAOBI)
DO $$ BEGIN
    CREATE TYPE recipe_line_type AS ENUM ('PHOI', 'NHAN_CHINH', 'NHAN_PHU', 'TRANG_TRI', 'BAOBI');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- lot_cost_status (V1)
DO $$ BEGIN
    CREATE TYPE lot_cost_status AS ENUM ('CONFIRMED', 'PENDING');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- lot_status (V1)
DO $$ BEGIN
    CREATE TYPE lot_status AS ENUM ('ACTIVE', 'CANCELLED', 'EXPIRED', 'PARTIAL', 'SOLD_OUT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- reconcile_status (V1)
DO $$ BEGIN
    CREATE TYPE reconcile_status AS ENUM ('PENDING', 'OK', 'OVER', 'UNDER', 'DISCREPANCY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- payment_status (V1 base + V12 adds DEPOSIT)
DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('UNPAID', 'DEPOSIT', 'PARTIAL', 'PAID');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- stock_lot_status (V1)
DO $$ BEGIN
    CREATE TYPE stock_lot_status AS ENUM ('AVAILABLE', 'DEPLETED', 'EXPIRED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- batch_run_type (V1)
DO $$ BEGIN
    CREATE TYPE batch_run_type AS ENUM ('DAILY_AUTO', 'WEEKLY_AUTO', 'MANUAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- batch_status (V1)
DO $$ BEGIN
    CREATE TYPE batch_status AS ENUM ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL', 'SUCCESS');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- file_type (V1)
DO $$ BEGIN
    CREATE TYPE file_type AS ENUM (
        'PRODUCTION_REQUEST', 'PRODUCTION_ACTUAL', 'DAILY_INVENTORY',
        'POS_EXPORT', 'RECIPE', 'SEMI_PRODUCT', 'PRODUCT',
        'INGREDIENT_PRICE'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- command_action (V7)
DO $$ BEGIN
    CREATE TYPE command_action AS ENUM ('CREATE', 'UPDATE', 'DELETE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- command_status (V7)
DO $$ BEGIN
    CREATE TYPE command_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- entity_status (V7)
DO $$ BEGIN
    CREATE TYPE entity_status AS ENUM ('ACTIVE', 'INACTIVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- branch_type (V11)
DO $$ BEGIN
    CREATE TYPE branch_type AS ENUM ('KHO_TONG', 'KHO_BEP', 'SHOP');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- transfer_status (V12)
DO $$ BEGIN
    CREATE TYPE transfer_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- transfer_confirm_status (V12)
DO $$ BEGIN
    CREATE TYPE transfer_confirm_status AS ENUM ('PENDING', 'CONFIRMED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- plan_status (V12)
DO $$ BEGIN
    CREATE TYPE plan_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- request_type (V12)
DO $$ BEGIN
    CREATE TYPE request_type AS ENUM ('DAILY', 'CUSTOMER_ORDER', 'URGENT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- request_status (V12)
DO $$ BEGIN
    CREATE TYPE request_status AS ENUM (
        'PENDING', 'APPROVED', 'REJECTED', 'IN_PRODUCTION', 'COMPLETED', 'CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- transaction_type (V14 — thay movement_type)
DO $$ BEGIN
    CREATE TYPE transaction_type AS ENUM (
        'IMPORT', 'EXPORT', 'RETURN', 'ADJUSTMENT', 'DISCARD', 'STOCK_COUNT'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- reference_type (V14 — thay movement_reason, là sub-type của transaction_type)
-- IMPORT      : DAILY | PURCHASE_ORDER | DAMAGE_REPLACEMENT | TRANSFER_IN
-- EXPORT      : TO_KITCHEN | TO_STORE | TRANSFER_OUT
-- RETURN      : TO_STORAGE | TO_SUPPLIER | INVALID_LIST | INVALID_QUALITY | INVALID_QUANTITY
-- DISCARD     : DAMAGED | EXPIRED
-- ADJUSTMENT  : INCREASE | DECREASE
-- STOCK_COUNT : END_OF_DAY | SPOT_CHECK
DO $$ BEGIN
    CREATE TYPE reference_type AS ENUM (
        'DAILY', 'PURCHASE_ORDER', 'DAMAGE_REPLACEMENT', 'TRANSFER_IN',
        'TO_KITCHEN', 'TO_STORE', 'TRANSFER_OUT',
        'TO_STORAGE', 'TO_SUPPLIER', 'INVALID_LIST', 'INVALID_QUALITY', 'INVALID_QUANTITY',
        'DAMAGED', 'EXPIRED',
        'INCREASE', 'DECREASE',
        'END_OF_DAY', 'SPOT_CHECK'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- write_off_reason (V12)
DO $$ BEGIN
    CREATE TYPE write_off_reason AS ENUM ('EXPIRED', 'DAMAGED', 'MOLD', 'OTHER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- supplier_return_status (V12)
DO $$ BEGIN
    CREATE TYPE supplier_return_status AS ENUM (
        'PENDING', 'SENT_TO_SUPPLIER', 'REPLACEMENT_RECEIVED', 'WRITTEN_OFF'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- customer_order_status (V12)
DO $$ BEGIN
    CREATE TYPE customer_order_status AS ENUM (
        'PENDING', 'CONFIRMED', 'IN_PRODUCTION', 'READY', 'DELIVERED', 'CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
