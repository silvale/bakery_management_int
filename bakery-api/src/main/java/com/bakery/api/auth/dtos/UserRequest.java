package com.bakery.api.auth.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserRequest(
    @NotBlank @Size(min = 3, max = 50)
    String username,

    @Size(max = 200)
    String email,

    @Size(max = 200)
    String fullName,

    /** Bắt buộc khi tạo mới, optional khi update (null = giữ nguyên mật khẩu) */
    @Size(min = 6, max = 100)
    String password,

    /** UUID của role */
    UUID roleId,

    /**
     * UUID của branch — gắn data scope cho user.
     * Để null nếu user được xem tất cả (SUPER_ADMIN, KHO_TRUONG).
     */
    UUID branchId
) {}
