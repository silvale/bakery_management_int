package com.bakery.api.master.dto;

import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SemiProductResponse extends BaseResponse {

    private String code;
    private String name;
    private String unit;
}
