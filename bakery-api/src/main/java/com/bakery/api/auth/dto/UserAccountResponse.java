package com.bakery.api.auth.dto;

import java.util.UUID;

import com.bakery.api.auth.entity.UserAccount;
import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserAccountResponse extends BaseResponse {

    private String username;
    private String fullName;
    private UUID roleId;
    private String roleCode;
    private String roleName;

    public static UserAccountResponse from(UserAccount a) {
        UserAccountResponse res = new UserAccountResponse();
        res.applyFrom(a);
        res.username = a.getUsername();
        res.fullName = a.getFullName();
        if (a.getRole() != null) {
            res.roleId = a.getRole().getId();
            res.roleCode = a.getRole().getCode();
            res.roleName = a.getRole().getName();
        }
        return res;
    }
}
