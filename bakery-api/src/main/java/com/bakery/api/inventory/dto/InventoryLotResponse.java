package com.bakery.api.inventory.dto;

import com.bakery.common.entity.enums.ItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Chi tiết 1 lô tồn kho (hàng trong 1 phiếu nhập/chuyển).
 */
@Data
@Builder
public class InventoryLotResponse {
    private UUID       id;
    private UUID       branchId;
    private String     branchName;
    private UUID       itemId;
    private String     itemCode;
    private String     itemName;
    private ItemType   itemType;
    private BigDecimal qtyAvailable;
    private String     lotNumber;
    private LocalDate  expiryDate;
    /** Ngày còn lại đến hết hạn (null nếu không có hạn dùng) */
    private Long       daysUntilExpiry;
    private BigDecimal costPerUnit;
    /** Phiếu gốc tạo ra lô này */
    private UUID       sourceTxId;
    private OffsetDateTime createdAt;
}
