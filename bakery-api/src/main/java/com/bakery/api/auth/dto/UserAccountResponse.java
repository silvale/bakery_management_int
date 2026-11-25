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
    /** {key = mã vai trò, name = tên}. */
    private com.bakery.framework.metadata.ReferenceValue role;
    private java.util.List<RefInfo> warehouses = new java.util.ArrayList<>();
    private java.util.List<RefInfo> priceSuppliers = new java.util.ArrayList<>();

    public static UserAccountResponse from(UserAccount a) {
        UserAccountResponse res = new UserAccountResponse();
        res.applyFrom(a);
        res.username = a.getUsername();
        res.fullName = a.getFullName();
        if (a.getRole() != null) {
            res.roleId = a.getRole().getId();
            res.roleCode = a.getRole().getCode();
            res.roleName = a.getRole().getName();
            res.role = new com.bakery.framework.metadata.ReferenceValue(a.getRole().getCode(), a.getRole().getName());
        }
        a.getWarehouses().stream().sorted(java.util.Comparator.comparing(com.bakery.api.master.entity.Warehouse::getCode))
                .forEach(w -> res.warehouses.add(new RefInfo(w.getId(), w.getCode(), w.getName(),
                        w.getKind() != null ? w.getKind().name() : null)));
        a.getPriceSuppliers().stream().sorted(java.util.Comparator.comparing(com.bakery.api.master.entity.Supplier::getCode))
                .forEach(s -> res.priceSuppliers.add(RefInfo.of(s.getId(), s.getCode(), s.getName())));
        return res;
    }
}
