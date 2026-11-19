package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CakeDesignResponse extends BaseResponse {
    private String code;
    private String name;
    private UUID productId;
    private ReferenceValue product;
    private String groupCode;
    private ReferenceValue group;
    private String imageUrl;
    private boolean active;
    private int sortOrder;
    private List<AccessoryResponse> accessories;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AccessoryResponse {
        private UUID itemId;
        private ReferenceValue item;
        private BigDecimal qty;
        /** Đơn vị gốc của item. */
        private String unit;
    }
}
