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
    /** 11/D6: true khi user không có quyền xem giá của NCC phiếu này → các trường giá của dòng là null. */
    private boolean priceHidden;
    private Integer sortOrder;
    private String note;

    // ── Packaging ────────────────────────────────────────────────
    /** Packaging đã chọn. null nếu không dùng packaging. */
    private PackagingRef packaging;
    /** Số lượng mua theo đơn vị đóng gói (số bao, số thùng...). */
    private BigDecimal purchaseQty;
    /** Số thực nhận theo unit; null khi phiếu chưa xác nhận. */
    private BigDecimal receivedQty;
    /**
     * Thành tiền (PURCHASE). Đã duyệt: theo receivedQty; chưa duyệt: theo số đặt.
     * Có bao: unitCost × qty ÷ qtyPerPack; không bao: unitCost × qty. Làm tròn 2 số lẻ.
     */
    private BigDecimal totalCost;

    // ── ADJUSTMENT (08) ──────────────────────────────────────────
    private BigDecimal systemQty;
    private BigDecimal countedQty;
    private String reasonCode;
    private ReferenceValue reason;
    /** Giá trị điều chỉnh (tăng +, giảm −), có sau khi duyệt. */
    private BigDecimal amount;
    /** Tồn hiện tại của item tại kho, tính lúc đọc; chỉ có khi phiếu chưa duyệt. */
    private BigDecimal currentQty;

    @Getter @Setter @NoArgsConstructor
    public static class PackagingRef {
        private java.util.UUID id;
        private String code;
        private String name;
        private BigDecimal qtyPerPack;
    }
}
