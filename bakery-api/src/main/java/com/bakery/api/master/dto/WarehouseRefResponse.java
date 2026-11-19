package com.bakery.api.master.dto;

import java.util.UUID;

/** Kho rút gọn cho dropdown kho xuất: chỉ id, mã, tên, loại. */
public record WarehouseRefResponse(UUID id, String code, String name, String type) {}
