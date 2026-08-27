-- Thêm cột image_url cho bảng item
-- Optional: null = chưa có ảnh
ALTER TABLE item ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
