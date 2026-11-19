package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một mẫu trong dòng thành phẩm của báo cáo ngày. */
@Getter
@Setter
@NoArgsConstructor
public class DailyReportLineDesignResponse {
    private UUID designId;
    private ReferenceValue design;
    private String imageUrl;
    private String groupCode;
    private BigDecimal qtyOpening;
    private BigDecimal qtyReceived;
    private BigDecimal qtyRemainingActual;
    private BigDecimal qtyCancelled;
    private BigDecimal qtySoldImplied;
}
