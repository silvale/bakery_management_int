package com.bakery.api.modules.sales.dtos;

import com.bakery.api.framework.enums.ItemType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PosSalesDataResponse {
    private UUID           id;
    private LocalDate      salesDate;
    private UUID           branchId;
    private String         branchName;
    private UUID           itemId;
    private String         itemCode;
    private String         itemName;
    private ItemType       itemType;
    private BigDecimal     qtySoldPos;
    private BigDecimal     revenue;
    private String         uploadedBy;
    private OffsetDateTime uploadedAt;
}
