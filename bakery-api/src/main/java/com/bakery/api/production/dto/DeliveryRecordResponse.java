package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeliveryRecordResponse {
    private UUID id;
    /** Tên sản phẩm — để hiển thị trên màn hình giao nhận */
    private String productName;
    private String productCode;
    private BigDecimal plannedQty;
    private BigDecimal qtyProduced;
    private BigDecimal qtyReceived;
    private BigDecimal discrepancy;
    private String deliveryStatus;
    private Instant confirmedAt;
    private String confirmedBy;
    private String note;
    /** EX_CODE được gán khi shop xác nhận giao nhận (tra từ product_mapping) */
    private String assignedExCode;
    /** Số bánh theo mẫu (bánh kem); [] khi không dùng mẫu. */
    private java.util.List<com.bakery.api.cake.dto.DeliveryRecordDesignResponse> designs = new java.util.ArrayList<>();
    /** 02/BE-13: phụ kiện bếp dùng theo mẫu (mặc định và thực dùng); [] khi không có. */
    private java.util.List<com.bakery.api.cake.dto.DeliveryRecordAccessoryResponse> accessories = new java.util.ArrayList<>();
    /** Mẫu cửa hàng nhận khác mẫu bếp làm ra. */
    private boolean designMismatch;
    /** Chỉ có ở response của confirm: cảnh báo thiếu phụ kiện (rỗng nếu đủ). */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<String> stockWarnings;
}
