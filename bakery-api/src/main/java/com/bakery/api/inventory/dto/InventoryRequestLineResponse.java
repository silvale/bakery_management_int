package com.bakery.api.inventory.dto;

import java.math.BigDecimal;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InventoryRequestLineResponse extends BaseResponse {

    private ReferenceValue item;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitCost;
    private Integer sortOrder;
    private String note;

    // ── Packaging ────────────────────────────────────────────────
    /** Packaging đã chọn. null nếu không dùng packaging. */
    private PackagingRef packaging;
    /** Số lượng mua theo đơn vị đóng gói (số bao, số thùng...). */
    private BigDecimal purchaseQty;
    /** Thành tiền = purchaseQty × unitCost. */
    private BigDecimal totalCost;

    @Getter @Setter @NoArgsConstructor
    public static class PackagingRef {
        private java.util.UUID id;
        private String code;
        private String name;
        private BigDecimal qtyPerPack;
    }
}
