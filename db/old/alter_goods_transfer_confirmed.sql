-- Migrate goods_transfer: approved_by/approved_at → confirmed_by/confirmed_at
-- Chạy lệnh này trên DB đang chạy nếu table đã tồn tại với cột cũ

DO $$
BEGIN
    -- Rename approved_by → confirmed_by (nếu cột cũ tồn tại)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer' AND column_name = 'approved_by'
    ) THEN
        ALTER TABLE goods_transfer RENAME COLUMN approved_by TO confirmed_by;
        RAISE NOTICE 'Renamed approved_by → confirmed_by';
    END IF;

    -- Rename approved_at → confirmed_at (nếu cột cũ tồn tại)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer' AND column_name = 'approved_at'
    ) THEN
        ALTER TABLE goods_transfer RENAME COLUMN approved_at TO confirmed_at;
        RAISE NOTICE 'Renamed approved_at → confirmed_at';
    END IF;

    -- Nếu status CHECK constraint cũ không có CONFIRMED/COMPLETED thì drop + recreate
    -- (chỉ cần nếu DB tạo trước khi có init.sql mới)
    ALTER TABLE goods_transfer DROP CONSTRAINT IF EXISTS chk_gt_status;
    ALTER TABLE goods_transfer ADD CONSTRAINT chk_gt_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'REJECTED', 'CANCELLED'));

    -- Thêm transfer_reason nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer' AND column_name = 'transfer_reason'
    ) THEN
        ALTER TABLE goods_transfer
            ADD COLUMN transfer_reason VARCHAR(30) NOT NULL DEFAULT 'PRODUCTION';
        ALTER TABLE goods_transfer ADD CONSTRAINT chk_gt_reason
            CHECK (transfer_reason IN ('PRODUCTION','RESTOCK','RETURN','ADJUSTMENT','WASTE_DISPOSAL'));
        RAISE NOTICE 'Added transfer_reason';
    END IF;

    -- Thêm source_transfer_id nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer' AND column_name = 'source_transfer_id'
    ) THEN
        ALTER TABLE goods_transfer
            ADD COLUMN source_transfer_id UUID REFERENCES goods_transfer(id);
        RAISE NOTICE 'Added source_transfer_id to goods_transfer';
    END IF;
END $$;

-- goods_transfer_line: qty → qty_requested, thêm qty_confirmed + qty_discrepancy
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer_line' AND column_name = 'qty'
    ) THEN
        ALTER TABLE goods_transfer_line RENAME COLUMN qty TO qty_requested;
        RAISE NOTICE 'Renamed qty → qty_requested';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'goods_transfer_line' AND column_name = 'qty_confirmed'
    ) THEN
        ALTER TABLE goods_transfer_line ADD COLUMN qty_confirmed   NUMERIC(18,4);
        ALTER TABLE goods_transfer_line ADD COLUMN qty_discrepancy NUMERIC(18,4);
        RAISE NOTICE 'Added qty_confirmed + qty_discrepancy';
    END IF;
END $$;

-- ingredient_stock_lot: thêm source_transfer_id nếu chưa có
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ingredient_stock_lot' AND column_name = 'source_transfer_id'
    ) THEN
        ALTER TABLE ingredient_stock_lot
            ADD COLUMN source_transfer_id UUID REFERENCES goods_transfer(id);
        RAISE NOTICE 'Added source_transfer_id to ingredient_stock_lot';
    END IF;
END $$;

SELECT 'Migration complete' AS result;
