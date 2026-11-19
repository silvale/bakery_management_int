package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.List;

import com.bakery.api.cake.dto.DesignQtyRequest;
import jakarta.validation.constraints.NotNull;

/** Body của {@code POST /delivery-records/{id}/confirm}. */
public record ConfirmDeliveryRequest(
        @NotNull BigDecimal qtyReceived,
        String note,
        /** Số bánh theo mẫu thực nhận — bắt buộc khi phiếu giao có mẫu và qtyReceived > 0. */
        List<DesignQtyRequest> designs) {}
