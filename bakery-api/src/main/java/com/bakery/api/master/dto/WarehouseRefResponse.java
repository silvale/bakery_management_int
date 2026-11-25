package com.bakery.api.master.dto;

import java.util.UUID;

/** Kho rút gọn cho dropdown kho xuất: chỉ id, mã, tên, kind (INTERNAL | EXTERNAL). */
public record WarehouseRefResponse(UUID id, String code, String name, String kind) {}
