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
    /** 20: tình trạng lấy NL của phiếu SEMI: NONE | IN_PROGRESS | TO_SETTLE | READY_TO_CLOSE | CLOSED. null với DAILY/ORDER. */
    private String materialStatus;
    /** 02/BE-13: cảnh báo thiếu phụ kiện lúc bếp Hoàn thành (không chặn); luôn có, rỗng nếu đủ. Batch gom cả phiếu. */
    private List<String> stockWarnings = new java.util.ArrayList<>();
}
