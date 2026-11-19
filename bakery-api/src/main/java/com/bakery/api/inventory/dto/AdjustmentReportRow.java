package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/** 08/BE-3: một dòng thống kê điều chỉnh theo (kho, mặt hàng, lý do, đơn vị). Số giảm là số dương. */
public record AdjustmentReportRow(UUID warehouseId, ReferenceValue warehouse, UUID itemId, ReferenceValue item,
                                  String imageUrl, String unit, String reasonCode, ReferenceValue reason,
                                  BigDecimal qtyIncrease, BigDecimal qtyDecrease,
                                  BigDecimal amountIncrease, BigDecimal amountDecrease, long requestCount) {}
