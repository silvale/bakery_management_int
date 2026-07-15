-- V21: Dọn dẹp column không sử dụng
--
-- command_request.error_detail — field errorDetail tồn tại trong entity
-- nhưng AbstractBakeryAdminService.log() không bao giờ set giá trị.
-- Luôn NULL. Bỏ để tránh nhầm lẫn.

ALTER TABLE command_request
    DROP COLUMN IF EXISTS error_detail;
