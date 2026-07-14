-- thresholdPercent: nếu set, nhóm FREE_GROUP chỉ sản xuất khi tồn < threshold% × target.
-- NULL = luôn sản xuất đủ target (hành vi cũ).
ALTER TABLE production_group ADD COLUMN IF NOT EXISTS threshold_percent SMALLINT;
