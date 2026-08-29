-- Add columns to Envers audit table to match inventory_request_line
ALTER TABLE inventory_request_line_his
    ADD COLUMN IF NOT EXISTS packaging_id  UUID,
    ADD COLUMN IF NOT EXISTS purchase_qty  DECIMAL(15, 4);
