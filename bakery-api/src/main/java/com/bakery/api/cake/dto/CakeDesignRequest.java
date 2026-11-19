package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Khai báo mẫu bánh. {@code accessories} thay toàn bộ danh sách phụ kiện khi cập nhật. */
public record CakeDesignRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 150) String name,
        @NotNull UUID productId,
        String groupCode,
        Boolean active,
        Integer sortOrder,
        @Valid List<Accessory> accessories) {

    public record Accessory(@NotNull UUID itemId, @NotNull BigDecimal qty) {}
}
