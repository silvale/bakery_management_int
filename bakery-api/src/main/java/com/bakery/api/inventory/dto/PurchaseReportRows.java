package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/** Dòng báo cáo nhập hàng theo thực nhận (06/BE-2, 06/BE-3). */
public final class PurchaseReportRows {
    private PurchaseReportRows() {}

    public record Packaging(String name, BigDecimal qtyPerPack) {}

    /** Một cặp (NCC, mặt hàng, đơn vị). {@code supplierId} null = phiếu không có NCC. */
    public record Summary(UUID supplierId, ReferenceValue supplier, UUID itemId, ReferenceValue item,
                          String imageUrl, String unit, BigDecimal qtyReceived, BigDecimal amount,
                          long requestCount, Packaging defaultPackaging) {}

    /** Một dòng phiếu nhận thiếu so với đặt. */
    public record Shortage(UUID requestId, String code, LocalDate receivedDate, String receivedBy,
                           UUID supplierId, ReferenceValue supplier, ReferenceValue warehouse,
                           UUID itemId, ReferenceValue item, String imageUrl, String unit,
                           BigDecimal qtyOrdered, BigDecimal qtyReceived, BigDecimal qtyShort,
                           BigDecimal unitCost, BigDecimal amountShort, String note) {}
}
