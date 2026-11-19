-- V55 (08/BE-4): lọc phiếu kho theo mặt hàng của dòng (EXISTS trên inventory_request_line.item_id)
CREATE INDEX IF NOT EXISTS idx_inv_req_line_item_id ON inventory_request_line (item_id);
