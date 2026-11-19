package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/** Phụ kiện bếp dùng cho một mẫu: mặc định (theo bộ) và thực dùng. {@code designId}/{@code design} null nếu thêm ngoài bộ không gắn mẫu. */
public record DeliveryRecordAccessoryResponse(UUID designId, ReferenceValue design, UUID itemId, ReferenceValue item,
                                              String unit, BigDecimal qtyDefault, BigDecimal qtyUsed) {}
