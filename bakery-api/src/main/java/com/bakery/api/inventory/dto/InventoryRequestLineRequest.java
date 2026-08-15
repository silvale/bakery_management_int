package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryRequestLineRequest(
        UUID itemId,
        BigDecimal quantity,
        String unit,
        /** Giá mua thực tế — bắt buộc với PURCHASE. Đơn giá theo packaging (giá/bao, giá/thùng). */
        BigDecimal unitCost,
        Integer sortOrder,
        String note,
        /** ID của packaging được chọn (Bao 10kg, Thùng 12 chai...). null nếu không dùng packaging. */
        UUID packagingId,
        /** Số lượng theo đơn vị đóng gói (số bao, số thùng...). quantity = purchaseQty × packaging.qtyPerPack. */
        BigDecimal purchaseQty) {}
