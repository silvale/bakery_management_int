package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Body của POST /inventory-requests/{id}/receive — phải gửi đủ mọi dòng của phiếu. */
public record ReceiveRequest(List<Line> lines) {
    public record Line(UUID lineId, BigDecimal receivedQty) {}
}
