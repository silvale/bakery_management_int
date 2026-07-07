package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.enums.ItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tổng tồn kho của 1 item tại 1 chi nhánh (gộp từ tất cả lô).
 * Dùng cho màn hình xem kho (tổng quan).
 */
@Data
@Builder
public class InventoryStockResponse {
    private UUID       branchId;
    private String     branchName;
    private UUID       itemId;
    private String     itemCode;
    private String     itemName;
    private ItemType   itemType;
    /** Tổng qty khả dụng từ tất cả lô còn hàng */
    private BigDecimal totalQtyAvailable;
    /** Số lô đang có hàng */
    private int        activeLotCount;
    /** Ngày hết hạn sớm nhất trong các lô còn hàng (null = không có hạn) */
    private java.time.LocalDate earliestExpiryDate;
}
