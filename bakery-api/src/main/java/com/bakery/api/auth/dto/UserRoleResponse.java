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

    public static UserRoleResponse from(UserRole r) {
        UserRoleResponse res = new UserRoleResponse();
        res.applyFrom(r);
        res.code = r.getCode();
        res.name = r.getName();
        res.description = r.getDescription();
        return res;
    }
}
