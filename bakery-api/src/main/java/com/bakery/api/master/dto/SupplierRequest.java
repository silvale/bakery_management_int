package com.bakery.api.master.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank String code,
        @NotBlank String name,
        String contactName,
        String phone,
        String email,
        String address,
        /** "Mua lẻ / trả ngay" — JSON {@code isCashPurchase}. null = giữ nguyên khi sửa, false khi tạo. */
        Boolean isCashPurchase) {}
