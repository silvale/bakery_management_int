-- V24: Unit Conversion table
-- Dùng để quy đổi đơn vị khi tính giá cost trong RecipeCostService.
-- Ví dụ: NL lưu giá theo KG, công thức dùng G → factor = 0.001 (G→KG)
--
-- Quy tắc: cost_in_recipe_unit = unit_cost_of_item * factor
--   tức là: factor = (1 đơn vị recipe) / (1 đơn vị item)
--   Ví dụ: item.unit=KG, line.unit=G → factor = 1/1000 = 0.001

CREATE TABLE IF NOT EXISTS unit_conversion (
    from_unit  VARCHAR(20)    NOT NULL,  -- đơn vị trong recipe line
    to_unit    VARCHAR(20)    NOT NULL,  -- đơn vị của item (unit_cost tính theo to_unit)
    factor     NUMERIC(20, 8) NOT NULL,  -- from_unit * factor = to_unit
    note       VARCHAR(200),
    PRIMARY KEY (from_unit, to_unit)
);

-- ── Khối lượng ────────────────────────────────────────────────────
INSERT INTO unit_conversion (from_unit, to_unit, factor, note) VALUES
  ('G',    'KG',   0.001,    '1g = 0.001 kg'),
  ('KG',   'G',    1000,     '1 kg = 1000 g'),
  ('KG',   'KG',   1,        'identity'),
  ('G',    'G',    1,        'identity')
ON CONFLICT (from_unit, to_unit) DO NOTHING;

-- ── Thể tích ──────────────────────────────────────────────────────
INSERT INTO unit_conversion (from_unit, to_unit, factor, note) VALUES
  ('ML',   'L',    0.001,    '1 ml = 0.001 l'),
  ('L',    'ML',   1000,     '1 l = 1000 ml'),
  ('L',    'L',    1,        'identity'),
  ('ML',   'ML',   1,        'identity')
ON CONFLICT (from_unit, to_unit) DO NOTHING;

-- ── Đơn vị đếm — identity ─────────────────────────────────────────
INSERT INTO unit_conversion (from_unit, to_unit, factor, note) VALUES
  ('CAI',  'CAI',  1,        'identity'),
  ('HOP',  'HOP',  1,        'identity'),
  ('GOI',  'GOI',  1,        'identity'),
  ('THUNG','THUNG',1,        'identity')
ON CONFLICT (from_unit, to_unit) DO NOTHING;
