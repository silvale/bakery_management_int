package com.bakery.api.auth.dto;

import java.util.List;
import java.util.UUID;

public record UserAccountRequest(
        String username,
        String fullName,
        UUID roleId,
        /** Plaintext password — sẽ được BCrypt hash trong service. Null = giữ nguyên khi update. */
        String password,
        /** Kho được gán (11/D2). Tạo mới mà không gửi → theo kho mặc định của vai trò; sửa mà không gửi → giữ nguyên. */
        List<UUID> warehouseIds,
        /** NCC được xem/nhập giá (11/D3). Tạo mới mà không gửi và vai trò có quyền giá → mọi NCC "mua lẻ / trả ngay". */
        List<UUID> priceSupplierIds
) {}
