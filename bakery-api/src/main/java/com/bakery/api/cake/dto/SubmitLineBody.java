package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Body tùy chọn của {@code POST /daily-reports/{id}/submit-line}: còn lại / hủy theo mẫu. */
public record SubmitLineBody(List<DesignRemaining> designs) {

    public record DesignRemaining(UUID designId, BigDecimal qtyRemaining, BigDecimal qtyCancelled) {}
}
