-- =============================================================
-- fix_bao_chay_kitchen_to_shop.sql
-- Mục đích: Di chuyển tồn kho "Bánh Bao Chay" từ KITCHEN → SHOP
-- Áp dụng cho delivery_record đã CONFIRMED trước khi fix confirmDelivery()
--
-- Cách chạy: Paste trực tiếp vào DBeaver / psql — KHÔNG cần BEGIN/COMMIT bên ngoài.
-- DO $$ tự chạy trong transaction riêng; rollback tự động nếu có lỗi.
-- =============================================================

DO $$
DECLARE
    v_kitchen_id   UUID;
    v_shop_id      UUID;
    v_item_id      UUID;
    v_lot          RECORD;
    v_to_transfer  NUMERIC := 0;
    v_taken        NUMERIC;
    v_new_lot_id   UUID;
    v_qty_received NUMERIC;
BEGIN
    -- Warehouse IDs
    SELECT id INTO v_kitchen_id FROM warehouse WHERE code = 'KITCHEN';
    SELECT id INTO v_shop_id    FROM warehouse WHERE code = 'SHOP';

    IF v_kitchen_id IS NULL OR v_shop_id IS NULL THEN
        RAISE NOTICE 'ABORT: Không tìm thấy warehouse KITCHEN hoặc SHOP';
        RETURN;
    END IF;

    -- Item ID của Bánh Bao Chay
    SELECT id INTO v_item_id FROM item WHERE name ILIKE '%Bánh Bao Chay%' LIMIT 1;

    IF v_item_id IS NULL THEN
        RAISE NOTICE 'ABORT: Không tìm thấy item "Bánh Bao Chay"';
        RETURN;
    END IF;

    RAISE NOTICE 'KITCHEN id: %, SHOP id: %, item id: %', v_kitchen_id, v_shop_id, v_item_id;

    -- Tổng qty đã confirm hôm nay (lấy production_date = ngày hiện tại)
    SELECT COALESCE(SUM(dr.qty_received), 0)
    INTO v_qty_received
    FROM delivery_record dr
    JOIN production_request_line prl ON prl.id = dr.production_request_line_id
    JOIN production_request pr       ON pr.id  = prl.production_request_id
    WHERE prl.item_id = v_item_id
      AND dr.delivery_status = 'CONFIRMED'
      AND pr.production_date = CURRENT_DATE;

    IF v_qty_received = 0 THEN
        RAISE NOTICE 'ABORT: Không tìm thấy delivery CONFIRMED nào cho Bánh Bao Chay hôm nay (%).',
                     CURRENT_DATE;
        RETURN;
    END IF;

    RAISE NOTICE 'Cần chuyển: % cái', v_qty_received;
    v_to_transfer := v_qty_received;

    -- FEFO: duyệt StockLot tại KITCHEN theo received_date ASC
    FOR v_lot IN
        SELECT *
        FROM stock_lot
        WHERE item_id     = v_item_id
          AND warehouse_id = v_kitchen_id
          AND qty_remaining > 0
        ORDER BY received_date ASC, created_at ASC
    LOOP
        EXIT WHEN v_to_transfer <= 0;

        v_taken := LEAST(v_lot.qty_remaining, v_to_transfer);

        -- Trừ KITCHEN lot
        UPDATE stock_lot
        SET qty_remaining = qty_remaining - v_taken,
            updated_at    = NOW()
        WHERE id = v_lot.id;

        -- OUT movement tại KITCHEN
        INSERT INTO stock_movement (lot_id, movement_type, qty, ref_type, note, approval_status)
        VALUES (v_lot.id, 'OUT', v_taken, 'DELIVERY_FIX',
                'Fix manual: chuyển KITCHEN→SHOP Bánh Bao Chay', 'APPROVED');

        -- StockLot mới tại SHOP
        INSERT INTO stock_lot (item_id, warehouse_id, qty_initial, qty_remaining, unit_cost,
                               received_date, expiry_date, status, approval_status)
        VALUES (v_item_id, v_shop_id, v_taken, v_taken,
                v_lot.unit_cost, v_lot.received_date, v_lot.expiry_date,
                'ACTIVE', 'APPROVED')
        RETURNING id INTO v_new_lot_id;

        -- IN movement tại SHOP
        INSERT INTO stock_movement (lot_id, movement_type, qty, ref_type, note, approval_status)
        VALUES (v_new_lot_id, 'IN', v_taken, 'DELIVERY_FIX',
                'Fix manual: nhận từ KITCHEN Bánh Bao Chay', 'APPROVED');

        RAISE NOTICE '  Lot %: lấy % → SHOP lot %', v_lot.id, v_taken, v_new_lot_id;
        v_to_transfer := v_to_transfer - v_taken;
    END LOOP;

    IF v_to_transfer > 0 THEN
        RAISE NOTICE 'CẢNH BÁO: Thiếu tồn KITCHEN, còn % chưa chuyển được', v_to_transfer;
    ELSE
        RAISE NOTICE 'HOÀN THÀNH: chuyển % cái Bánh Bao Chay KITCHEN → SHOP thành công.', v_qty_received;
    END IF;
END $$;
