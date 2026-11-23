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
    /** Vai trò mặc định (7 vai trò seed): không xóa, không đổi mã (18/D2, D3). */
    @com.fasterxml.jackson.annotation.JsonProperty("isSystem")
    private boolean system;
    private String defaultWarehouseCodes;
    private boolean priceBySupplier;
    /** 18: false chỉ với SUPER_ADMIN (D4) — FE không tự đoán theo code. */
    @com.fasterxml.jackson.annotation.JsonProperty("canEditPermissions")
    private boolean canEditPermissions;

    public static UserRoleResponse from(UserRole r) {
        UserRoleResponse res = new UserRoleResponse();
        res.applyFrom(r);
        res.code = r.getCode();
        res.name = r.getName();
        res.description = r.getDescription();
        res.system = r.isSystem();
        res.defaultWarehouseCodes = r.getDefaultWarehouseCodes();
        res.priceBySupplier = r.isPriceBySupplier();
        res.canEditPermissions = !com.bakery.api.auth.service.UserRoleService.isSuperAdmin(r);
        return res;
    }
}
