package com.bakery.api.production.dto;

import java.time.LocalDate;
import java.util.List;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.entity.ProductionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductionRequestResponse extends BaseResponse {
    private String code;
    private ProductionType productionType;
    private LocalDate productionDate;
    private String note;
    private List<ProductionRequestLineResponse> lines;
    /** 24/§6: chi phí NL của phiếu (đã dùng, hư hỏng, tổng). Chỉ chi tiết phiếu và chỉ khi user xem được giá vốn; null nếu không. */
    private com.bakery.api.production.dto.ProductionPoolDto.MaterialCost materialCost;
    /** 02/BE-13: cảnh báo thiếu phụ kiện lúc bếp Hoàn thành (không chặn); luôn có, rỗng nếu đủ. Batch gom cả phiếu. */
    private List<String> stockWarnings = new java.util.ArrayList<>();
}
