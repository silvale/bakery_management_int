-- =============================================================================
-- V14: Refactor inventory_movement — idempotent, safe to re-run
-- Dùng EXCEPTION WHEN để bắt lỗi tại chỗ, không làm abort transaction ngoài
-- =============================================================================

-- 1. reference_type (doc pointer) → source_type  [PHẢI làm trước để free tên]
DO $$
BEGIN
    ALTER TABLE inventory_movement RENAME COLUMN reference_type TO source_type;
    RAISE NOTICE 'OK: reference_type → source_type';
EXCEPTION
    WHEN undefined_column THEN
        RAISE NOTICE 'SKIP: reference_type không tồn tại (đã đổi rồi)';
    WHEN duplicate_column THEN
        RAISE NOTICE 'SKIP: source_type đã tồn tại rồi';
END $$;

-- 2. reference_id → source_id
DO $$
BEGIN
    ALTER TABLE inventory_movement RENAME COLUMN reference_id TO source_id;
    RAISE NOTICE 'OK: reference_id → source_id';
EXCEPTION
    WHEN undefined_column THEN
        RAISE NOTICE 'SKIP: reference_id không tồn tại';
    WHEN duplicate_column THEN
        RAISE NOTICE 'SKIP: source_id đã tồn tại rồi';
END $$;

-- 3. movement_type → transaction_type
DO $$
BEGIN
    ALTER TABLE inventory_movement RENAME COLUMN movement_type TO transaction_type;
    RAISE NOTICE 'OK: movement_type → transaction_type';
EXCEPTION
    WHEN undefined_column THEN
        RAISE NOTICE 'SKIP: movement_type không tồn tại';
    WHEN duplicate_column THEN
        RAISE NOTICE 'SKIP: transaction_type đã tồn tại rồi';
END $$;

-- 4. reason → reference_type  [tên đã free sau bước 1]
DO $$
BEGIN
    ALTER TABLE inventory_movement RENAME COLUMN reason TO reference_type;
    RAISE NOTICE 'OK: reason → reference_type';
EXCEPTION
    WHEN undefined_column THEN
        RAISE NOTICE 'SKIP: reason không tồn tại';
    WHEN duplicate_column THEN
        RAISE NOTICE 'SKIP: reference_type đã tồn tại rồi';
END $$;

-- 5. Thêm reference_code
ALTER TABLE inventory_movement
    ADD COLUMN IF NOT EXISTS reference_code VARCHAR(50);

-- 6. Map old string values → new enum values (safe to re-run)
UPDATE inventory_movement SET reference_type = 'DAILY'        WHERE reference_type = 'DAILY_IMPORT';
UPDATE inventory_movement SET reference_type = 'PURCHASE_ORDER' WHERE reference_type = 'PO_IMPORT';
UPDATE inventory_movement SET transaction_type = 'ADJUSTMENT', reference_type = 'INCREASE' WHERE reference_type = 'ADJUSTMENT_IN';
UPDATE inventory_movement SET transaction_type = 'ADJUSTMENT', reference_type = 'DECREASE' WHERE reference_type = 'ADJUSTMENT_OUT';
UPDATE inventory_movement SET transaction_type = 'RETURN',     reference_type = 'TO_STORAGE'  WHERE reference_type = 'RETURN_IN';
UPDATE inventory_movement SET transaction_type = 'RETURN',     reference_type = 'TO_SUPPLIER' WHERE reference_type = 'RETURN_OUT';
UPDATE inventory_movement SET reference_type = 'TO_KITCHEN'   WHERE reference_type = 'EXPORT_TO_KITCHEN';
UPDATE inventory_movement SET reference_type = 'TO_STORE'     WHERE reference_type = 'EXPORT_TO_SHOP';
UPDATE inventory_movement SET transaction_type = 'DISCARD',   reference_type = 'DAMAGED' WHERE reference_type = 'WRITE_OFF';
UPDATE inventory_movement SET source_type = 'ADJUSTMENT'      WHERE source_type = 'InventoryAdjustment';
UPDATE inventory_movement SET source_type = 'WRITE_OFF'       WHERE source_type = 'InventoryWriteOff';

-- Verify
SELECT transaction_type, reference_type, count(*)
FROM inventory_movement
GROUP BY 1, 2
ORDER BY 1, 2;
