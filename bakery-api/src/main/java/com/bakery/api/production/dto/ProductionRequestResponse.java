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
}
