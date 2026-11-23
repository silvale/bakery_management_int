-- V60: chuẩn hóa đơn vị & đóng gói (16/BE-1)
-- Một nguồn đơn vị là bảng unit. Chạy trong một transaction: lỗi ở bước nào thì rollback toàn bộ.
--   1. thêm 4 đơn vị mới (D2)
--   2. đổi mã sai sang mã đúng ở mọi cột đơn vị, trừ bảng *_his (D3), rồi D4 (Vitamin C)
--   3. chốt chặn: còn mã lạ ở item.unit / item_packaging.code thì RAISE EXCEPTION
--   4. FK sang unit(code) + index
--   5. bỏ cột item_packaging.name (tên lấy theo unit.name)

-- ── 1. Đơn vị mới ───────────────────────────────────────────────────────────
INSERT INTO unit (code, name) VALUES
    ('BAO',     'Bao'),
    ('TO',      'Tờ'),
    ('LOC',     'Lốc'),
    ('HOP1000', 'Hộp 1000')
ON CONFLICT (code) DO NOTHING;

-- ── 2. Đổi mã ───────────────────────────────────────────────────────────────
-- unit_fix(x): cắt khoảng trắng, chuẩn Unicode, viết hoa, áp bảng D3. Trả mã mới CHỈ khi mã mới có trong unit,
-- ngược lại trả nguyên x (để chốt chặn bước 3 báo đúng giá trị gốc).
CREATE FUNCTION pg_temp.unit_fix(x text) RETURNS text LANGUAGE sql STABLE AS $f$
    SELECT CASE WHEN m IN (SELECT code FROM public.unit) THEN m ELSE x END
    FROM (SELECT CASE k
                     WHEN 'PCS'  THEN 'CAI'
                     WHEN 'CÁI'  THEN 'CAI'
                     WHEN 'HỘP'  THEN 'HOP'
                     WHEN 'CÂY'  THEN 'CAY'
                     WHEN 'GRAM' THEN 'G'
                     WHEN 'BỘ'   THEN 'BO'
                     ELSE k
                 END AS m
          FROM (SELECT upper(normalize(btrim(x), NFC)) AS k) a) b
$f$;

DO $$
DECLARE
    r    record;
    n    bigint;
    bad  text;
BEGIN
    -- Đổi mã mà trùng khóa thì báo, không tự gộp
    SELECT string_agg(format('item_id=%s code=%s (%s dòng)', item_id, nc, c), '; ') INTO bad
    FROM (SELECT item_id, pg_temp.unit_fix(code) AS nc, count(*) AS c
          FROM item_packaging GROUP BY 1, 2 HAVING count(*) > 1) d;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'item_packaging: đổi mã sẽ trùng (item_id, code), cần xử lý tay: %', bad;
    END IF;

    SELECT string_agg(format('%s→%s (%s dòng)', f, t, c), '; ') INTO bad
    FROM (SELECT pg_temp.unit_fix(from_unit) AS f, pg_temp.unit_fix(to_unit) AS t, count(*) AS c
          FROM unit_conversion GROUP BY 1, 2 HAVING count(*) > 1) d;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'unit_conversion: đổi mã sẽ trùng khóa, cần xử lý tay: %', bad;
    END IF;

    -- Mọi cột đơn vị (tên 'unit' hoặc '%_unit', kiểu chữ), bỏ *_his; thêm item_packaging.code
    FOR r IN
        SELECT c.table_name, c.column_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = current_schema()
          AND c.data_type IN ('character varying', 'text')
          AND c.table_name NOT LIKE '%\_his'
          AND c.table_name <> 'unit'
          AND (c.column_name = 'unit' OR c.column_name LIKE '%\_unit'
               OR (c.table_name = 'item_packaging' AND c.column_name = 'code'))
        ORDER BY c.table_name, c.column_name
    LOOP
        EXECUTE format('UPDATE %I SET %I = pg_temp.unit_fix(%I) WHERE %I IS DISTINCT FROM pg_temp.unit_fix(%I)',
                       r.table_name, r.column_name, r.column_name, r.column_name, r.column_name);
        GET DIAGNOSTICS n = ROW_COUNT;
        RAISE NOTICE 'V60 đổi mã: %.% → % dòng', r.table_name, r.column_name, n;

        EXECUTE format('SELECT count(*) FROM %I WHERE %I IS NOT NULL AND %I NOT IN (SELECT code FROM unit)',
                       r.table_name, r.column_name, r.column_name) INTO n;
        IF n > 0 THEN
            RAISE NOTICE 'V60 còn mã lạ: %.% → % dòng', r.table_name, r.column_name, n;
        END IF;
    END LOOP;

    -- D4: Vitamin C giữ đơn vị chính VIEN, đóng gói HOP1000 = 1000
    UPDATE item_packaging SET code = 'HOP1000'
    WHERE qty_per_pack = 1000 AND code <> 'HOP1000'
      AND item_id = (SELECT id FROM item WHERE code = 'ING-VITMIN-C');
    INSERT INTO item_packaging (item_id, code, qty_per_pack, name, is_default)
    SELECT i.id, 'HOP1000', 1000, 'Hộp 1000', true
    FROM item i
    WHERE i.code = 'ING-VITMIN-C'
      AND NOT EXISTS (SELECT 1 FROM item_packaging p WHERE p.item_id = i.id);

    -- ── 3. Chốt chặn ───────────────────────────────────────────────────────
    SELECT string_agg(format('item.unit=%L (%s dòng)', unit, c), '; ') INTO bad
    FROM (SELECT unit, count(*) AS c FROM item
          WHERE unit IS NOT NULL AND unit NOT IN (SELECT code FROM unit) GROUP BY unit) d;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Mã đơn vị lạ, không có trong bảng unit: %', bad;
    END IF;

    SELECT string_agg(format('item_packaging.code=%L (%s dòng)', code, c), '; ') INTO bad
    FROM (SELECT code, count(*) AS c FROM item_packaging
          WHERE code NOT IN (SELECT code FROM unit) GROUP BY code) d;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'Mã đơn vị lạ, không có trong bảng unit: %', bad;
    END IF;
END $$;

-- ── 4. FK + index (index cũng phục vụ kiểm "đã dùng" ở BE-2/BE-3/BE-4) ─────────
CREATE INDEX idx_item_unit ON item(unit);
CREATE INDEX idx_item_packaging_code ON item_packaging(code);
CREATE INDEX idx_inv_req_line_packaging ON inventory_request_line(packaging_id) WHERE packaging_id IS NOT NULL;
CREATE INDEX idx_stock_lot_packaging ON stock_lot(packaging_id) WHERE packaging_id IS NOT NULL;

ALTER TABLE item           ADD CONSTRAINT fk_item_unit           FOREIGN KEY (unit) REFERENCES unit(code);
ALTER TABLE item_packaging ADD CONSTRAINT fk_item_packaging_unit FOREIGN KEY (code) REFERENCES unit(code);

-- ── 5. Bỏ tên đóng gói ───────────────────────────────────────────────────────
ALTER TABLE item_packaging DROP COLUMN name;
