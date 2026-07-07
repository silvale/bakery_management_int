package com.bakery.api.modules.inventory.dtos;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.enums.ItemType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Filter cho InventoryQueryController.
 * Extends AdminFilter → Spring MVC tự bind: page, size, search, entityStatus.
 *
 * GET /api/v1/inventory/active?branchId=xxx&itemType=INGREDIENT&page=0&size=20
 */
@Getter
@Setter
public class InventoryFilter extends AdminFilter {

    /** ID chi nhánh — required khi gọi getStock */
    private UUID branchId;

    /** Lọc theo loại item: INGREDIENT | PRODUCT | SEMI_PRODUCT */
    private ItemType itemType;
}
