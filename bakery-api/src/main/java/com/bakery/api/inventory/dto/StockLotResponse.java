package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StockLotResponse extends BaseResponse {

    private ReferenceValue item;
    private ReferenceValue warehouse;
    private ReferenceValue supplier;
    private BigDecimal qtyInitial;
    private BigDecimal qtyRemaining;
    private BigDecimal totalQtyRemaining;   // dùng cho /summary — tổng tất cả lot
    private BigDecimal unitCost;
    /** 11/D6: giá lô bị ẩn vì user không được xem giá NCC của lô. */
    private boolean priceHidden;
    private LocalDate receivedDate;
    private LocalDate expiryDate;

    // dùng cho /summary — phân loại hàng
    private String itemType;        // INGREDIENT | SEMI_PRODUCT | PRODUCT
    private String unit;
    private ReferenceValue itemGroup; // chỉ PRODUCT; null nếu chưa gán
    /** 07/BE-2 (chỉ /summary): "Ngưỡng nhập" = item.min_stock_quantity và "Mức nhập" = item.restock_quantity; null nếu chưa đặt. */
    private BigDecimal minStockQuantity;
    private BigDecimal restockQuantity;
    /** 07: quy cách mặc định của item (chỉ /summary); null nếu không có. Không quy đổi số tồn. */
    private DefaultPackaging defaultPackaging;

    /** {@code unit} = đơn vị gốc của item. Ví dụ "Hộp 100" = 100 cái. */
    public record DefaultPackaging(String name, BigDecimal qtyPerPack, String unit) {}
}
