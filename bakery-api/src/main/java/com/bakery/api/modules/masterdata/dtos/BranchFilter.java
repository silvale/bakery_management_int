package com.bakery.api.modules.masterdata.dtos;

import com.bakery.api.framework.dtos.AdminFilter;
import com.bakery.api.framework.enums.BranchType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchFilter extends AdminFilter {
    /** Lọc theo loại kho: KHO_TONG | KHO_BEP | SHOP — null = tất cả */
    private BranchType branchType;
}
