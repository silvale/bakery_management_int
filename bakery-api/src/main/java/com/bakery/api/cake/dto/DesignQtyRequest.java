package com.bakery.api.cake.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Số bánh của một mẫu (bếp hoàn thành hoặc cửa hàng xác nhận).
 *
 * @param accessories chỉ dùng khi bếp Hoàn thành (02/BE-13): phụ kiện thực dùng cho mẫu này. null = mặc định (bộ phụ kiện
 *                    của mẫu × qty); {@code []} = mẫu này không dùng phụ kiện. Cửa hàng xác nhận bỏ qua field này.
 */
public record DesignQtyRequest(UUID designId, BigDecimal qty, List<AccessoryQty> accessories) {

    public DesignQtyRequest(UUID designId, BigDecimal qty) { this(designId, qty, null); }

    /** Phụ kiện thực dùng: số lượng &gt; 0 theo đơn vị gốc của mặt hàng, cho phép số lẻ. */
    public record AccessoryQty(UUID itemId, BigDecimal qty) {}
}
