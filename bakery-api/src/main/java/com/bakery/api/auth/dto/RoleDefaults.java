package com.bakery.api.auth.dto;

import java.util.List;

/** {@code GET /user-roles/{id}/defaults}: kho và NCC xem giá điền sẵn khi chọn vai trò. */
public record RoleDefaults(List<RefInfo> warehouses, List<RefInfo> priceSuppliers) {}
