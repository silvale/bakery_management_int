package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.enums.SemiProductType;
import lombok.Getter;
import lombok.Setter;

/**
 * Filter cho SemiProductAdminController.
 * GET /admin/semi-products/active?search=phoi&type=PHOI&page=0&size=20
 */
@Getter
@Setter
public class SemiProductFilter extends AdminFilter {

    /** Lọc theo loại: PHOI | NHAN */
    private SemiProductType type;
}
