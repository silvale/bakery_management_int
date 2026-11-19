package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng báo cáo mẫu bánh: số liệu cộng dồn của mẫu trong khoảng ngày (chỉ báo cáo FINALIZED). */
@Getter
@Setter
@NoArgsConstructor
public class CakeDesignReportRow {
    private UUID designId;
    private ReferenceValue design;
    private String code;
    private String imageUrl;
    private ReferenceValue product;
    private ReferenceValue group;
    private BigDecimal qtyReceived = BigDecimal.ZERO;
    private BigDecimal qtySold = BigDecimal.ZERO;
    private BigDecimal qtyCancelled = BigDecimal.ZERO;
    /** Còn lại ở báo cáo FINALIZED mới nhất trong khoảng. */
    private BigDecimal qtyRemainingLast;
}
