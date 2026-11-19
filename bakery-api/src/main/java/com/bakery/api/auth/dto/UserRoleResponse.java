package com.bakery.api.auth.dto;

import com.bakery.api.auth.entity.UserRole;
import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleResponse extends BaseResponse {

    private String code;
    private String name;
    private String description;
    /** 11/D1: vai trò hệ thống — giai đoạn 1 không sửa/xóa được. */
    @com.fasterxml.jackson.annotation.JsonProperty("isSystem")
    private boolean system;
    private String defaultWarehouseCodes;
    private boolean priceBySupplier;

    public static UserRoleResponse from(UserRole r) {
        UserRoleResponse res = new UserRoleResponse();
        res.applyFrom(r);
        res.code = r.getCode();
        res.name = r.getName();
        res.description = r.getDescription();
        res.system = r.isSystem();
        res.defaultWarehouseCodes = r.getDefaultWarehouseCodes();
        res.priceBySupplier = r.isPriceBySupplier();
        return res;
    }
}
