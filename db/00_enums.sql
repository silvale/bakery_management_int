-- =============================================================
-- 00_enums.sql  —  Consolidated enum types (Architecture V2)
--
-- Removed (obsolete with Single-Table Ledger architecture):
--   stock_lot_status, transfer_status, transfer_confirm_status,
--   plan_status, transaction_type (V14), reference_type (V14),
--   write_off_reason, supplier_return_status
--
-- NOTE: inventory_transaction.transaction_type / transaction_reason
--       / status are VARCHAR in SQL for flexibility; Java enums
--       enforce valid values at application layer.
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ---------------------------------------------------------------------------
-- PRODUCT / INGREDIENT MASTER
-- ---------------------------------------------------------------------------

DO $$ BEGIN
    CREATE TYPE product_type AS ENUM ('STANDARD', 'SHEET_CAKE', 'ACCESSORY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE base_unit AS ENUM ('GRAM', 'ML', 'PCS');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE semi_product_type AS ENUM ('PHOI', 'NHAN', 'TRANG_TRI');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE recipe_line_type AS ENUM ('PHOI', 'NHAN_CHINH', 'NHAN_PHU', 'TRANG_TRI', 'BAOBI');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- PRODUCTION
-- ---------------------------------------------------------------------------

-- Used in production_lot.cost_status
DO $$ BEGIN
    CREATE TYPE lot_cost_status AS ENUM ('CONFIRMED', 'PENDING');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Used in production_lot.status
DO $$ BEGIN
    CREATE TYPE lot_status AS ENUM ('ACTIVE', 'CANCELLED', 'EXPIRED', 'PARTIAL', 'SOLD_OUT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Used in production_order.status (replaces reconcile_status)
DO $$ BEGIN
    CREATE TYPE production_order_status AS ENUM ('PENDING', 'OK', 'OVER', 'UNDER', 'DISCREPANCY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- PAYMENTS & ORDERS
-- ---------------------------------------------------------------------------

-- Used in inventory_transaction.payment_status + customer_order
DO $$ BEGIN
    CREATE TYPE payment_status AS ENUM ('UNPAID', 'DEPOSIT', 'PARTIAL', 'PAID');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE customer_order_status AS ENUM (
        'PENDING', 'CONFIRMED', 'IN_PRODUCTION', 'READY', 'DELIVERED', 'CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- BATCH / IMPORT
-- ---------------------------------------------------------------------------

DO $$ BEGIN
    CREATE TYPE batch_run_type AS ENUM ('DAILY_AUTO', 'WEEKLY_AUTO', 'MANUAL');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE batch_status AS ENUM ('RUNNING', 'COMPLETED', 'FAILED', 'PARTIAL', 'SUCCESS');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE file_type AS ENUM (
        'PRODUCTION_REQUEST', 'PRODUCTION_ACTUAL', 'DAILY_INVENTORY',
        'POS_EXPORT', 'RECIPE', 'SEMI_PRODUCT', 'PRODUCT', 'INGREDIENT_PRICE'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- ADMIN FRAMEWORK
-- ---------------------------------------------------------------------------

DO $$ BEGIN
    CREATE TYPE command_action AS ENUM ('CREATE', 'UPDATE', 'DELETE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE command_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE entity_status AS ENUM ('ACTIVE', 'INACTIVE');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- BRANCH / WAREHOUSE
-- ---------------------------------------------------------------------------

DO $$ BEGIN
    CREATE TYPE branch_type AS ENUM ('KHO_TONG', 'KHO_BEP', 'SHOP');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ---------------------------------------------------------------------------
-- PRODUCTION REQUEST
-- ---------------------------------------------------------------------------

DO $$ BEGIN
    CREATE TYPE request_type AS ENUM ('DAILY', 'CUSTOMER_ORDER', 'URGENT');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE request_status AS ENUM (
        'PENDING', 'APPROVED', 'REJECTED', 'IN_PRODUCTION', 'COMPLETED', 'CANCELLED'
    );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
