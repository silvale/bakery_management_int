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
public class DailyShopReportResponse {
    private UUID          id;
    private LocalDate     reportDate;
    private UUID          branchId;
    private String        branchName;
    private UUID          itemId;
    private String        itemCode;
    private String        itemName;
    private ItemType      itemType;
    private BigDecimal    qtyLeftoverTheoretical;
    private BigDecimal    qtyDestroyedActual;
    /** Mất mát không rõ lý do = lý thuyết - thực tế hủy */
    private BigDecimal    unexplainedLoss;
    private String        submittedBy;
    private OffsetDateTime submittedAt;
    private String        note;
}
