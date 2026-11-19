package com.bakery.api.auth.dto;

import java.util.UUID;

/** id + mã + tên (kho, NCC, vai trò) dùng trong response user. {@code type} chỉ có ở kho. */
public record RefInfo(UUID id, String code, String name, String type) {
    public static RefInfo of(UUID id, String code, String name) { return new RefInfo(id, code, name, null); }
}
